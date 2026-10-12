#!/usr/bin/env python3
"""Idempotently configure GCP monitoring using the operator's gcloud login."""

import argparse
import json
import subprocess
import urllib.request
import urllib.error


def configure(project, instance, email, gcloud):
    token = subprocess.check_output([gcloud, 'auth', 'print-access-token'], text=True).strip()
    base = f'https://monitoring.googleapis.com/v3/projects/{project}/'

    def request(path, body=None, method=None):
        query = urllib.request.Request(base + path,
                                       data=json.dumps(body).encode() if body is not None else None,
                                       method=method,
                                       headers={'Authorization': 'Bearer ' + token,
                                                'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(query, timeout=30) as response:
                return json.load(response)
        except urllib.error.HTTPError as exception:
            raise RuntimeError(exception.read().decode()) from exception

    def upsert(collection, body):
        key = 'displayName'
        existing = next((item for item in request(collection).get(collection, [])
                         if item.get(key) == body[key]), None)
        if existing:
            body['name'] = existing['name']
            return request(existing['name'].split(f'projects/{project}/')[1], body, 'PATCH')
        return request(collection, body, 'POST')

    def logging_metric(name, descriptor, **fields):
        endpoint = f'https://logging.googleapis.com/v2/projects/{project}/metrics/{name}'
        body = {'name': name, 'filter': f'resource.type="gce_instance" AND resource.labels.instance_id="{instance}" '
                                      'AND jsonPayload.status:* AND jsonPayload.uri!="/actuator/health" '
                                      'AND jsonPayload.uri!="/readyz" AND NOT jsonPayload.uri=~"^/actuator(/|$)" '
                                      'AND jsonPayload.uri=~"^/api/"',
                'metricDescriptor': descriptor, **fields}
        query = urllib.request.Request(endpoint, data=json.dumps(body).encode(), method='PUT',
                                       headers={'Authorization': 'Bearer ' + token,
                                                'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(query, timeout=30) as response:
                json.load(response)
        except urllib.error.HTTPError as exception:
            if exception.code != 404:
                raise RuntimeError(exception.read().decode()) from exception
            query = urllib.request.Request(endpoint.rsplit('/', 1)[0], data=json.dumps(body).encode(), method='POST',
                                           headers={'Authorization': 'Bearer ' + token,
                                                    'Content-Type': 'application/json'})
            with urllib.request.urlopen(query, timeout=30) as response:
                json.load(response)

    channel = upsert('notificationChannels', {'type': 'email', 'displayName': 'FleaFlea operations email',
                                             'labels': {'email_address': email}, 'enabled': True})
    uptime = upsert('uptimeCheckConfigs', {
        'displayName': 'FleaFlea API HTTPS', 'period': '60s', 'timeout': '10s',
        'monitoredResource': {'type': 'uptime_url', 'labels': {'project_id': project, 'host': 'api.fleaflea.app'}},
        'httpCheck': {'requestMethod': 'GET', 'path': '/actuator/health', 'port': 443,
                      'useSsl': True, 'validateSsl': True},
        'contentMatchers': [{'content': '"status":"UP"', 'matcher': 'CONTAINS_STRING'}]})

    def policy(name, condition):
        body = {'displayName': 'FleaFlea ' + name, 'combiner': 'OR', 'enabled': True,
                'conditions': [{'displayName': name, **condition}],
                'notificationChannels': [channel['name']], 'alertStrategy': {'autoClose': '1800s'},
                'documentation': {'content': 'Check GCP VM, Docker health, Nginx and FleaFlea operations logs. '
                                             'Do not restart the database before checking the incident.',
                                  'mimeType': 'text/markdown'}}
        if 'conditionMatchedLog' in condition:
            body['alertStrategy']['notificationRateLimit'] = {'period': '300s'}
        result = upsert('alertPolicies', body)
        print(result['displayName'])

    resource_filter = f'resource.type = "gce_instance" AND resource.labels.instance_id = "{instance}"'

    def threshold(name, metric, value, duration='300s', extra='', aligner='ALIGN_MEAN'):
        policy(name, {'conditionThreshold': {
            'filter': resource_filter + f' AND metric.type = "{metric}"' + extra,
            'comparison': 'COMPARISON_GT', 'thresholdValue': value, 'duration': duration,
            'aggregations': [{'alignmentPeriod': '60s', 'perSeriesAligner': aligner}],
            'trigger': {'count': 1}, 'evaluationMissingData': 'EVALUATION_MISSING_DATA_INACTIVE'}})

    uptime_id = uptime['name'].split('/')[-1]
    policy('API unavailable', {'conditionThreshold': {
        'filter': f'resource.type = "uptime_url" AND metric.type = "monitoring.googleapis.com/uptime_check/check_passed" '
                  f'AND metric.labels.check_id = "{uptime_id}"',
        'comparison': 'COMPARISON_GT', 'thresholdValue': 1, 'duration': '120s',
        'aggregations': [{'alignmentPeriod': '60s', 'perSeriesAligner': 'ALIGN_NEXT_OLDER',
                          'crossSeriesReducer': 'REDUCE_COUNT_FALSE'}], 'trigger': {'count': 1}}})
    threshold('CPU over 90 percent', 'compute.googleapis.com/instance/cpu/utilization', 0.9)
    threshold('Memory over 85 percent', 'agent.googleapis.com/memory/percent_used', 85,
              '600s', ' AND metric.labels.state = "used"')
    threshold('Disk over 85 percent', 'agent.googleapis.com/disk/percent_used', 85,
              extra=' AND metric.labels.state = "used" AND metric.labels.device != monitoring.regex.full_match(".*loop.*")')
    for name, limit in [('backup', 30 * 3600), ('restore', 8 * 86400), ('cleanup', 30 * 3600)]:
        threshold(name + ' overdue', f'custom.googleapis.com/fleaflea/{name}_age_seconds', limit)
    threshold('Unhealthy Docker container', 'custom.googleapis.com/fleaflea/unhealthy_containers', 0, '120s')
    policy('Operations heartbeat missing', {'conditionAbsent': {
        'filter': resource_filter + ' AND metric.type = "custom.googleapis.com/fleaflea/unhealthy_containers"',
        'duration': '1200s', 'aggregations': [{'alignmentPeriod': '300s', 'perSeriesAligner': 'ALIGN_MEAN'}],
        'trigger': {'count': 1}}})
    policy('Maintenance failed', {'conditionMatchedLog': {
        'filter': f'resource.type="gce_instance" AND resource.labels.instance_id="{instance}" '
                  'AND jsonPayload.event="operations_failed"'}})
    logging_metric('fleaflea_api_requests', {'metricKind': 'DELTA', 'valueType': 'INT64',
                                           'labels': [{'key': 'status', 'valueType': 'STRING'}]},
                   labelExtractors={'status': 'EXTRACT(jsonPayload.status)'})
    logging_metric('fleaflea_api_latency', {'metricKind': 'DELTA', 'valueType': 'DISTRIBUTION', 'unit': 's'},
                   valueExtractor='EXTRACT(jsonPayload.request_time)',
                   bucketOptions={'exponentialBuckets': {'numFiniteBuckets': 12, 'growthFactor': 2, 'scale': 0.005}})
    request_metric = resource_filter + ' AND metric.type="logging.googleapis.com/user/fleaflea_api_requests"'
    rate_aggregation = [{'alignmentPeriod': '60s', 'perSeriesAligner': 'ALIGN_RATE',
                         'crossSeriesReducer': 'REDUCE_SUM'}]
    error_policy = {
        'displayName': 'FleaFlea API 5xx ratio above 5 percent', 'combiner': 'AND', 'enabled': True,
        'notificationChannels': [channel['name']], 'alertStrategy': {'autoClose': '1800s'},
        'conditions': [
            {'displayName': '5xx ratio', 'conditionThreshold': {
                'filter': request_metric + ' AND metric.labels.status=monitoring.regex.full_match("5[0-9][0-9]")',
                'denominatorFilter': request_metric, 'aggregations': rate_aggregation,
                'denominatorAggregations': rate_aggregation, 'comparison': 'COMPARISON_GT',
                'thresholdValue': 0.05, 'duration': '300s', 'trigger': {'count': 1}}},
            {'displayName': 'At least 10 requests per minute', 'conditionThreshold': {
                'filter': request_metric, 'aggregations': [{'alignmentPeriod': '60s',
                'perSeriesAligner': 'ALIGN_DELTA', 'crossSeriesReducer': 'REDUCE_SUM'}],
                'comparison': 'COMPARISON_GT', 'thresholdValue': 9, 'duration': '300s', 'trigger': {'count': 1}}}]}
    upsert('alertPolicies', error_policy)
    threshold('API p95 latency over 1 second', 'logging.googleapis.com/user/fleaflea_api_latency', 1,
              aligner='ALIGN_PERCENTILE_95')
    print(json.dumps({'channel': channel['name'], 'verification': channel.get('verificationStatus'),
                      'uptime': uptime['name']}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--project', required=True)
    parser.add_argument('--instance-id', required=True)
    parser.add_argument('--email', required=True)
    parser.add_argument('--gcloud', default='gcloud')
    arguments = parser.parse_args()
    configure(arguments.project, arguments.instance_id, arguments.email, arguments.gcloud)
