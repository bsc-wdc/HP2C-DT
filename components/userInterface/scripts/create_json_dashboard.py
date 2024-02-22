import json
import os
import sys

def generate_panel(edge_name, device):
    targets = []
    for i in range(device[1]):
        cleaned_device_name = device[0].replace(" ", "")
        cleaned_device_name = cleaned_device_name.replace("-", "") + "Sensor" + str(i)
        query = f"SELECT * FROM \"{edge_name}\" WHERE device = \'{cleaned_device_name}\'\n"
        ref_id = f"{edge_name}-{cleaned_device_name}"
        target = {
            "alias": "phase " + str(i + 1),
            "datasource": {
                "type": "influxdb",
                "uid": "bed49e87-0f10-4248-beae-2bc1fb6c0c4f"
            },
            "groupBy": [
                {
                    "params": ["$__interval"],
                    "type": "time"
                },
                {
                    "params": ["null"],
                    "type": "fill"
                }
            ],
            "measurement": edge_name,
            "orderByTime": "ASC",
            "policy": "one_day_only",
            "query": query,
            "rawQuery": True,
            "refId": ref_id,
            "resultFormat": "time_series",
            "select": [
                [
                    {
                        "params": ["value"],
                        "type": "field"
                    },
                    {
                        "params": [],
                        "type": "last"
                    }
                ]
            ],
            "tags": []
        }
        targets.append(target)
    

    panel = {
        "datasource": {
            "type": "influxdb",
            "uid": "bed49e87-0f10-4248-beae-2bc1fb6c0c4f"
        },
        "fieldConfig": {
            "defaults": {
                "color": {"mode": "palette-classic"},
                "custom": {
                    "axisBorderShow": False,
                    "axisCenteredZero": False,
                    "axisColorMode": "text",
                    "axisLabel": "",
                    "axisPlacement": "auto",
                    "barAlignment": 0,
                    "drawStyle": "line",
                    "fillOpacity": 0,
                    "gradientMode": "none",
                    "hideFrom": {"legend": False, "tooltip": False, "viz": False},
                    "insertNulls": False,
                    "lineInterpolation": "linear",
                    "lineWidth": 1,
                    "pointSize": 5,
                    "scaleDistribution": {"type": "linear"},
                    "showPoints": "auto",
                    "spanNulls": False,
                    "stacking": {"group": ref_id, 
                                 "mode": "none"},
                    "thresholdsStyle": {"mode": "off"}
                },
                "mappings": [],
                "thresholds": {"mode": "absolute", "steps": [{"color": "green", "value": None}, {"color": "red", "value": 80}]},
                "unitScale": True
            },
            "overrides": []
        },
        "gridPos": {"h": 11, "w": 21, "x": 0, "y": 0},
        "options": {"legend": {"calcs": [], "displayMode": "list", "placement": "bottom", "showLegend": True}, "tooltip": {"mode": "single", "sort": "none"}},
        "targets": targets,
        "title": f"{edge_name} - {device[0]}",
        "type": "timeseries"
    }

    return panel

def generate_dashboard_json(deployment_name, edge_device_dict):
    panels = []
    for edge_name, devices in edge_device_dict.items():
        for device in devices:
            panel = generate_panel(edge_name, device)
            panels.append(panel)

    dashboard_title = f"HP2CDT - {deployment_name}"
    dashboard = {
        "dashboard": {
            "schemaVersion": 39,
            "tags": [],
            "templating": {
                "list": []
            },
            "time": {
                "from": "now-15m",
                "to": "now"
            },
            "timepicker": {},
            "timezone": "",
            "annotations": {
                "list": [
                    {
                    "builtIn": 1,
                    "datasource": {
                        "type": "grafana",
                        "uid": "-- Grafana --"
                    },
                    "enable": True,
                    "hide": True,
                    "iconColor": "rgba(0, 211, 255, 1)",
                    "name": "Annotations & Alerts",
                    "type": "dashboard"
                    }
                ]
            },
            "liveNow": True,
            "refresh": "5",
            "title": dashboard_title,
            "panels": panels,
            "weekStart": ""
        },
        "overwrite": True
    }

    directory = "jsons_dasboards"
    if not os.path.exists(directory):
        os.makedirs(directory)

    filename = os.path.join(directory, f"{deployment_name}_dashboard.json")
    with open(filename, "w") as f:
        json.dump(dashboard, f, indent=4)

    print(f"Dashboard JSON saved to {filename}")

def get_dict_from_deployment(deployment_name):
    deployments_dir = "../../../deployments"
    deployment_dir = os.path.join(deployments_dir, deployment_name, "setup")
    return get_devices(deployment_dir)

def get_devices(deployment_dir):
    edges = {}
    for edge_file_name in os.listdir(deployment_dir):
        if edge_file_name.endswith('.json'):
            edge_file_path = os.path.join(deployment_dir, edge_file_name)
            with open(edge_file_path, 'r') as f:
                edge_data = json.load(f)
                edge_name = edge_data['global-properties']['label']
                devices = []
                for d in edge_data['devices']:
                    device_name = d['label']
                    n_indexes = len(d['properties']['indexes'])
                    devices.append((device_name, n_indexes))
                edges[edge_name] = devices
    return edges
                

def main():
    if len(sys.argv) < 2: 
        deployment_name = "testbed"
    else: 
        deployment_name = sys.argv[1]
    edges = get_dict_from_deployment(deployment_name)
    generate_dashboard_json(deployment_name, edges)

if __name__ == "__main__" :
    main()
