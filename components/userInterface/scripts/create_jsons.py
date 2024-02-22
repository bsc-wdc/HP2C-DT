import json
import os
import sys

def generate_dashboard_json(deployment_name, edge_device_dict):
    panels = []
    for edge_name, devices in edge_device_dict.items():
        for device in devices:
            targets = []
            for i in range(device[1]):
                cleaned_device_name = device[0].replace(" ", "")
                cleaned_device_name = cleaned_device_name.replace("-", "") + "Sensor" + str(i)
                query = f"SELECT * FROM \'{edge_name}\' WHERE device = \'{cleaned_device_name}\'"
                ref_id = f"{edge_name}-{cleaned_device_name}"
                target = {
                            "refId": ref_id,
                            "expr": query
                        }
                targets.append(target)
            
            panel = {
                    "title": f"{edge_name} - {device[0]}",
                    "type": "graph",
                    "datasource": "influxdb", 
                    "targets": targets
                }
            panels.append(panel)

    dashboard_title = f"HP2CDT - {deployment_name}"
    dashboard = {
        "dashboard": {
            "title": dashboard_title,
            "panels": panels
        },
        "overwrite": True
    }

    directory = "dashboard_jsons"
    if not os.path.exists(directory):
        os.makedirs(directory)

    filename = os.path.join(directory, f"{deployment_name}_dashboard.json")
    with open(filename, "w") as f:
        json.dump(dashboard, f, indent=4)

    print(f"Dashboard JSON saved to {filename}")

def get_dict_from_deployment(deployment_name):
    print(os.getcwd())
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
