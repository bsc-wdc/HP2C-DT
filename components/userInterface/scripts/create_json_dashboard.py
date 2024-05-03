import json
import os
import sys

def generate_panel_timeseries(edge_name, device, datasource_uid):
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
                "uid": datasource_uid
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
            "uid": datasource_uid
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
                    "insertNulls": 180000,
                    "lineInterpolation": "linear",
                    "lineWidth": 1,
                    "pointSize": 5,
                    "scaleDistribution": {"type": "linear"},
                    "showPoints": "always",
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



def generate_dashboard_json(deployment_name, edge_device_dict, datasource_uid):
    panels = []
    for edge_name, devices in edge_device_dict.items():
        for device in devices:
            panel_timeseries = generate_panel_timeseries(edge_name, device, datasource_uid)
            panel_table = panel_timeseries.copy()
            names = []
            for i in range(len(panel_table["targets"])):
                if len(panel_table["targets"]) == 1: 
                    time = "Time"
                    names.append(time)
                elif i == 0: 
                    time = "phase " + str(i + 1) + " · Time"
                    names.append(time)
                names.append("phase " + str(i + 1))
                panel_table["targets"][i]["refId"] = "Table-" + panel_table["targets"][i]["refId"]
                q = panel_table["targets"][i]["query"].split()
                q[1] = "value"
                panel_table["targets"][i]["query"] = ' '.join(q) 

            panel_table["transformations"] = [
                {
                    "id": "concatenate",
                    "options": {}
                },
                {
                    "id": "filterFieldsByName",
                    "options": {
                        "include": {
                            "names": names
                        }
                    }
                },
                {
                    "id": "sortBy",
                    "options": {
                        "fields": {},
                        "sort": [
                            {
                            "desc": True,
                            "field": time
                            }
                        ]
                    }
                }
            ]
            panel_table["type"] = "table"
            panel_table["title"] = "(Table) " + panel_table["title"]
            panel_table["options"] = {
                "cellHeight": "md",
                "footer": {
                    "countRows": False,
                    "fields": "",
                    "reducer": [
                    "sum"
                    ],
                    "show": False
                },
                "showHeader": True
            }
            panels.append(panel_table)
            panels.append(panel_timeseries)

    dashboard_title = f"hp2cdt - {deployment_name}"
    dashboard = {
        "dashboard": {
            "schemaVersion": 39,
            "tags": [],
            "templating": {
                "list": []
            },
            "time": {
                "from": "now-5m",
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

    directory = "jsons_dashboards"
    if not os.path.exists(directory):
        os.makedirs(directory)

    filename = os.path.join(directory, f"{deployment_name}_dashboard.json")
    with open(filename, "w") as f:
        json.dump(dashboard, f, indent=4)

    print(f"Dashboard JSON saved to {filename}")

def get_dict_from_deployment(deployment_name, deployment_dir):
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
    deployment_name = sys.argv[1]
    deployment_dir = sys.argv[2]
    datasource_uid = sys.argv[3]
    edges = get_dict_from_deployment(deployment_name, deployment_dir)
    generate_dashboard_json(deployment_name, edges, datasource_uid)


def ui_exec(deployment_name, devices_info, datasource_uid):
    devices_data = json.loads(devices_info)
    edges = {}
    for edge, devices_info in devices_data.items():
        devices = []
        for device, info in devices_info.items():
            n_indexes = info["size"]
            devices.append((device, n_indexes))
        edges[edge] = devices
    generate_dashboard_json(deployment_name, edges, datasource_uid)

if __name__ == "__main__" :
    main()
