import http from 'k6/http';
import { check } from 'k6';
import { WebSocket } from 'k6/websockets';
import { setTimeout, setInterval, clearInterval } from 'k6/timers';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const pairs = new SharedArray('synthetic chat pairs', () => JSON.parse(open(__ENV.DATA_FILE)).pairs);
const baseUrl = __ENV.BASE_URL.replace(/\/$/, '');
const pairCount = Number(__ENV.PAIRS || 10);
const runSeconds = Number(__ENV.RUN_SECONDS || 45);
const intervalMs = Number(__ENV.MESSAGE_INTERVAL_MS || 3000);
const mode = __ENV.MODE || 'relay';
const strict = __ENV.EXPECT_RELAY !== 'false';
const runId = __ENV.RUN_ID || 'chat';

if (pairCount > pairs.length || intervalMs < 1100) throw new Error('Insufficient fixture pairs or unsafe per-member message rate');

const messageSent = new Counter('chat_message_sent');
const messageAcknowledged = new Counter('chat_message_acknowledged');
const messageReceived = new Counter('chat_message_received');
const missingMessages = new Rate('chat_missing');
const duplicateMessages = new Counter('chat_duplicates');
const outOfOrderMessages = new Counter('chat_out_of_order');
const relayLatency = new Trend('chat_relay_latency_ms', true);
const acknowledgementLatency = new Trend('chat_ack_latency_ms', true);
const readReceived = new Counter('chat_read_received');
const typingReceived = new Counter('chat_typing_received');
const socketErrors = new Counter('chat_socket_errors');
const businessErrors = new Counter('chat_business_errors');
const unexpectedDisconnects = new Counter('chat_unexpected_disconnects');
const recoveredMessages = new Counter('chat_recovered_messages');
const recoverySucceeded = new Rate('chat_recovery_succeeded');
const tokenExpirationSucceeded = new Rate('chat_token_expiration_succeeded');
const completedPairs = new Counter('chat_completed_pairs');

export const options = {
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: { chat: { executor: 'per-vu-iterations', vus: pairCount, iterations: 1,
    maxDuration: `${runSeconds + 35}s`, gracefulStop: '10s' } },
  thresholds: {
    checks: ['rate>0.99'],
    chat_completed_pairs: [`count==${pairCount}`],
    chat_duplicates: ['count==0'],
    chat_business_errors: ['count==0'],
    chat_socket_errors: ['count==0'],
    ...(strict && mode === 'relay' ? {
      chat_missing: ['rate==0'], chat_unexpected_disconnects: ['count==0'],
      chat_relay_latency_ms: ['p(95)<1000'],
    } : {}),
    ...(['reconnect', 'recovery'].includes(mode) ? { chat_recovery_succeeded: ['rate==1'] } : {}),
    ...(mode === 'expire' ? { chat_token_expiration_succeeded: ['rate==1'] } : {}),
  },
};

function frame(command, headers = {}, body = '') {
  return `${command}\n${Object.entries(headers).map(([key, value]) => `${key}:${value}`).join('\n')}\n\n${body}\0`;
}

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const random = Math.floor(Math.random() * 16);
    return (character === 'x' ? random : (random & 3) | 8).toString(16);
  });
}

export default function () {
  const pair = pairs[__VU - 1];
  const pendingMessages = new Map();
  const acknowledgedMessages = new Set();
  const receivedMessages = new Set();
  const connected = { sender: false, receiver: false };
  let senderSocket;
  let receiverSocket;
  let heartbeatTimer;
  let sendingTimer;
  let started = false;
  let finishing = false;
  let receiverOffline = false;
  let lastReceivedId = 0;
  let recoveryCursor = 0;
  let reconnectConfirmed = false;
  let expirationClosed = false;
  let sequence = 0;
  const senderNode = __ENV.SENDER_NODE || 'node-a';
  const receiverNode = __ENV.SAME_NODE === 'true' ? senderNode : senderNode === 'node-a' ? 'node-b' : 'node-a';

  // Initialize counters so a zero-error run still exports a measurable value.
  for (const metric of [duplicateMessages, outOfOrderMessages, socketErrors, businessErrors,
    unexpectedDisconnects, recoveredMessages, readReceived, typingReceived]) metric.add(0);

  function send(socket, destination, payload) {
    socket.send(frame('SEND', { destination, 'content-type': 'application/json' }, JSON.stringify(payload)));
  }

  function handleMessage(role, rawFrame) {
    const headerEnd = rawFrame.indexOf('\n\n');
    if (headerEnd < 0) return;
    const headers = Object.fromEntries(rawFrame.slice(0, headerEnd).split('\n').slice(1)
      .filter((line) => line.includes(':')).map((line) => {
        const colon = line.indexOf(':');
        return [line.slice(0, colon), line.slice(colon + 1)];
      }));
    const body = rawFrame.slice(headerEnd + 2);
    if (!body) return;
    let payload;
    try { payload = JSON.parse(body); } catch (_) { socketErrors.add(1); return; }
    if ((headers.destination || '').endsWith('chat-errors')) {
      businessErrors.add(1);
      return;
    }
    if ((headers.destination || '').endsWith('chat-acks') && role === 'sender' && payload.clientMessageId) {
      const sent = pendingMessages.get(payload.clientMessageId);
      if (sent && !acknowledgedMessages.has(payload.clientMessageId)) {
        acknowledgedMessages.add(payload.clientMessageId);
        messageAcknowledged.add(1);
        acknowledgementLatency.add(Date.now() - sent.sentAt);
      }
      return;
    }
    if (role !== 'receiver') return;
    if (payload.type === 'chat-message') {
      const message = payload.payload;
      if (!pendingMessages.has(message.clientMessageId)) return;
      if (receivedMessages.has(message.clientMessageId)) {
        duplicateMessages.add(1);
        return;
      }
      receivedMessages.add(message.clientMessageId);
      messageReceived.add(1);
      relayLatency.add(Date.now() - pendingMessages.get(message.clientMessageId).sentAt);
      if (message.id < lastReceivedId) outOfOrderMessages.add(1);
      lastReceivedId = Math.max(lastReceivedId, message.id);
      send(receiverSocket, `/app/chat/rooms/${pair.roomId}/read`, { messageId: message.id });
    } else if (payload.type === 'chat-read') {
      readReceived.add(1);
    } else if (payload.type === 'chat-typing') {
      typingReceived.add(1);
    }
  }

  function openSocket(role, node, token) {
    const socket = new WebSocket(`${baseUrl.replace(/^http/, 'ws')}/${node}/ws/chat`, [], {
      headers: { Origin: 'http://localhost:8080' }, tags: { name: `chat_${role}_${node}` },
    });
    let buffered = '';
    socket.addEventListener('open', () => socket.send(frame('CONNECT', {
      'accept-version': '1.2', 'heart-beat': '10000,10000', Authorization: `Bearer ${token}`,
    })));
    socket.addEventListener('message', (event) => {
      buffered += typeof event.data === 'string' ? event.data
        : String.fromCharCode(...new Uint8Array(event.data));
      let terminator;
      while ((terminator = buffered.indexOf('\0')) >= 0) {
        const receivedFrame = buffered.slice(0, terminator).replace(/^\n+/, '');
        buffered = buffered.slice(terminator + 1);
        if (receivedFrame.startsWith('CONNECTED\n')) {
          connected[role] = true;
          ['/user/queue/chat', '/user/queue/chat-acks', '/user/queue/chat-errors'].forEach((destination, index) =>
            socket.send(frame('SUBSCRIBE', { id: `${role}-${index}`, destination, ack: 'auto' })));
          if (connected.sender && connected.receiver && !started) startConversation();
          if (role === 'receiver' && receiverOffline) {
            receiverOffline = false;
            reconnectConfirmed = true;
          }
        } else if (receivedFrame.startsWith('MESSAGE\n')) {
          handleMessage(role, receivedFrame);
        } else if (receivedFrame.startsWith('ERROR\n')) {
          socketErrors.add(1);
        }
      }
    });
    socket.addEventListener('error', () => { if (!finishing && mode !== 'expire') socketErrors.add(1); });
    socket.addEventListener('close', (event) => {
      connected[role] = false;
      if (mode === 'expire' && role === 'receiver') expirationClosed = event.code === 1008;
      else if (!finishing && !(role === 'receiver' && receiverOffline)) unexpectedDisconnects.add(1);
    });
    return socket;
  }

  function emitMessage() {
    if (!connected.sender || finishing) return;
    const clientMessageId = uuid();
    const content = `${runId}:pair-${__VU}:seq-${++sequence}`;
    pendingMessages.set(clientMessageId, { sentAt: Date.now(), content });
    send(senderSocket, `/app/chat/rooms/${pair.roomId}/messages`, { content, clientMessageId });
    messageSent.add(1);
    send(senderSocket, `/app/chat/rooms/${pair.roomId}/typing`, { typing: sequence % 2 === 1 });
  }

  function startConversation() {
    started = true;
    heartbeatTimer = setInterval(() => {
      if (connected.sender) senderSocket.send('\n');
      if (connected.receiver) receiverSocket.send('\n');
    }, 10000);
    // The simple STOMP broker does not provide subscription receipt acknowledgements.
    setTimeout(() => {
      if (mode !== 'expire') {
        emitMessage();
        sendingTimer = setInterval(emitMessage, intervalMs);
      }
    }, 1500);
    if (mode === 'reconnect') {
      setTimeout(() => {
        recoveryCursor = lastReceivedId;
        receiverOffline = true;
        receiverSocket.close();
      }, Math.floor(runSeconds * 1000 / 3));
      setTimeout(() => {
        receiverSocket = openSocket('receiver', receiverNode, pair.receiver.token);
      }, Math.floor(runSeconds * 2000 / 3));
    }
    setTimeout(() => {
      if (sendingTimer) clearInterval(sendingTimer);
      setTimeout(finishConversation, 2500);
    }, runSeconds * 1000);
  }

  function finishConversation() {
    if (finishing) return;
    finishing = true;
    if (heartbeatTimer) clearInterval(heartbeatTimer);
    if (sendingTimer) clearInterval(sendingTimer);
    if (['reconnect', 'recovery'].includes(mode)) {
      const recovered = new Set(receivedMessages);
      let cursor = recoveryCursor;
      let more = true;
      let pages = 0;
      while (more && pages++ < 100) {
        const response = http.get(`${baseUrl}/${receiverNode}/api/v1/chat/rooms/${pair.roomId}/messages?afterId=${cursor}&size=100`, {
          headers: { Authorization: `Bearer ${pair.receiver.token}` }, tags: { name: 'chat_cursor_recovery' },
        });
        if (response.status !== 200) { check(response, { 'cursor recovery 200': () => false }); break; }
        const page = response.json();
        for (const message of page.content) {
          if (acknowledgedMessages.has(message.clientMessageId) && !recovered.has(message.clientMessageId)) {
            recovered.add(message.clientMessageId);
            recoveredMessages.add(1);
          }
        }
        more = page.hasNext;
        cursor = page.nextCursor;
      }
      recoverySucceeded.add((mode !== 'reconnect' || reconnectConfirmed)
        && [...acknowledgedMessages].every((id) => recovered.has(id)));
    }
    if (mode === 'expire') tokenExpirationSucceeded.add(expirationClosed && connected.sender);
    for (const id of acknowledgedMessages) missingMessages.add(!receivedMessages.has(id));
    check(started, { 'both STOMP clients connected': (value) => value });
    check(pendingMessages.size, { 'all sent messages acknowledged': (count) => mode === 'expire' || count === acknowledgedMessages.size });
    completedPairs.add(1);
    if (senderSocket.readyState === 1) senderSocket.close();
    if (receiverSocket.readyState === 1) receiverSocket.close();
  }

  senderSocket = openSocket('sender', senderNode, pair.sender.token);
  receiverSocket = openSocket('receiver', receiverNode, mode === 'expire' ? pair.receiver.shortToken : pair.receiver.token);
  setTimeout(() => {
    if (!started) finishConversation();
  }, 15000);
}

export function handleSummary(data) {
  return { [__ENV.SUMMARY_PATH || 'chat-summary.json']: JSON.stringify(data, null, 2) };
}
