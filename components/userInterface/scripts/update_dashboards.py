import os
import json
import requests
from requests import RequestException

from home.models import Edge, Device, Deployment
from scripts.create_json_dashboard import ui_exec


def dashboard_has_changed(server_url):
    return False


def update_dashboards():
    in_docker = False
    deployment_name = "testbed"
    if "DEPLOYMENT_NAME" in os.environ:
        deployment_name = os.getenv("DEPLOYMENT_NAME")
    setup_file = f"../../deployments/{deployment_name}/deployment_setup.json"
    if not os.path.exists(setup_file):
        in_docker = True
        setup_file = "/data/deployment_setup.json"

    setup_data = None
    if os.path.exists(setup_file):
        with open(setup_file, 'r') as f:
            json_data = f.read()
            setup_data = json.loads(json_data)
    if not setup_data:
        print("Deployment setup not found", flush=True)
        exit(1)
    if not in_docker:
        os.chdir("scripts")
    grafana_url, server_port, server_url = get_deployment_info(setup_data)
    edges_info = None
    try:
        response = requests.get(f"{server_url}/getEdgesInfo")
        edges_info = response.text
    except RequestException as _:
        if "LOCAL_IP" in os.environ:
            server_ip = os.getenv("LOCAL_IP")
            server_url = f"http://{server_ip}:{server_port}"
            response = requests.get(f"{server_url}/getEdgesInfo")
            edges_info = response.text

    if edges_info is None:
        print("Server doesn't respond", flush=True)
        exit(1)
    # If the method is running for the first time we must update the dashboard.
    # Otherwise, we will check if the dashboard has changed and, if it didn´t,
    # we will not update it.
    initial_execution = os.getenv("INITIAL_EXECUTION")
    if initial_execution is not None:
        initial_execution = int(initial_execution)
        if initial_execution == 0:
            changed = check_changes(edges_info)
            if not changed:
                if not in_docker:
                    os.chdir("..")
                return None, deployment_name, grafana_url, server_port, server_url
        else:
            os.environ["INITIAL_EXECUTION"] = "0"

    #################################### INIT #################################
    # Function to extract IP and port from JSON
    def get_ip_port(data):
        return f"{data['grafana']['ip']}:{data['grafana']['port']}"

    grafana_addr = get_ip_port(setup_data)

    database_ip = setup_data["database"]["ip"]
    # Grafana API URL and key
    GRAFANA_URL = f"http://{grafana_addr}"
    GRAFANA_PORT = setup_data['grafana']['port']

    config_file = "../../../config.json" if os.path.isfile(
        "../../../config.json") else "/run/secrets/config.json"

    with open(config_file, 'r') as f:
        config_data = json.load(f)
    GRAFANA_API_KEY = config_data["grafana"]["api_key"]
    DATABASE_USERNAME = config_data["database"]["username"]
    DATABASE_PASSWORD = config_data["database"]["password"]
    # Define a list of GRAFANA_URLs
    URLs = [GRAFANA_URL]
    # Check if LOCAL_IP is not empty and add it to the list
    LOCAL_IP = os.getenv("LOCAL_IP", None)
    if LOCAL_IP:
        URLs.append(f"http://{LOCAL_IP}:{GRAFANA_PORT}")
        
    ############################ GET DATASOURCE UID ######################################
    datasource_uid = ""

    for url in URLs:
        print(f"Trying URL {url} to handle datasource UID...", flush=True)
        try:
            response = requests.get(f"{url}/api/datasources", headers={
                "Authorization": f"Bearer {GRAFANA_API_KEY}"})
            data = response.json()
            for item in data:
                if item["name"] == "influxdb":
                    datasource_uid = item["uid"]
                    response = requests.delete(
                        f"{url}/api/datasources/uid/{datasource_uid}",
                        headers={"Authorization": f"Bearer {GRAFANA_API_KEY}"})
                    print(f"Delete response {response.text}", flush=True)
                    break
            if datasource_uid:
                break
        except RequestException as _:
            print("Error requesting to url: ", url, flush=True)

    ############################ CREATE DATASOURCE ############################
    INFLUXDB_JSON = {
        "name": "influxdb",
        "type": "influxdb",
        "typeName": "InfluxDB",
        "typeLogoUrl": "/public/app/plugins/datasource/influxdb/img/influxdb_logo.svg",
        "access": "proxy",
        "url": f"http://{database_ip}:8086",
        "user": DATABASE_USERNAME,
        "database": "",
        "basicAuth": False,
        "isDefault": True,
        "jsonData": {"dbName": "hp2cdt"},
        "secureJsonData": {"password": DATABASE_PASSWORD},
        "readOnly": False
    }

    datasource_uid = ""
    for url in URLs:
        try:
            headers = {
                "Authorization": f"Bearer {GRAFANA_API_KEY}",
                "Content-Type": "application/json"
            }
            response = requests.post(f"{url}/api/datasources", headers=headers,
                                     json=INFLUXDB_JSON)
            if response.status_code == 200:
                datasource_list = requests.get(f"{url}/api/datasources",
                                               headers=headers).json()
                for item in datasource_list:
                    if item["name"] == "influxdb":
                        datasource_uid = item["uid"]
                        break
        except RequestException as _:
            print("Error requesting to url: ", url, flush=True)
    if not datasource_uid:
        print("Datasource influxdb uid not found")
        exit(1)

    ########################### CREATE JSON DASHBOARD ####################################
    # Execute the Python script to create the dashboard JSON
    ui_exec(deployment_name, edges_info, datasource_uid)

    ########################### CREATE OR UPDATE DASHBOARD ###############################
    # Path to the dashboard JSON file
    DASHBOARD_JSON = f"jsons_dashboards/{deployment_name}_dashboard.json"

    # Iterate over the list of URLs and try to make the request
    final_url = ""
    for url in URLs:
        try:
            with open(DASHBOARD_JSON, 'r') as f:
                dashboard_data = json.load(f)
            response = requests.post(f"{url}/api/dashboards/db", headers={
                "Authorization": f"Bearer {GRAFANA_API_KEY}",
                "Content-Type": "application/json"}, json=dashboard_data)
            if response.status_code == 200:
                final_url = url
                break
            else:
                print(response.text, flush=True)
        except RequestException as _:
            print("Error requesting to url: ", url, flush=True)

    if final_url:
        print(f"Dashboard created or updated successfully using {final_url}.", flush=True)
    else:
        print("Failed to create or update the dashboard", flush=True)
        exit(1)

    ############################## GET DASHBOARDS #########################################
    os.makedirs("dashboards", exist_ok=True)

    for url in URLs:
        try:
            response = requests.get(f"{url}/api/search", headers={
                "Authorization": f"Bearer {GRAFANA_API_KEY}"})
            data = response.json()
            for item in data:
                uid = item["uid"]
                dashboard_info_response = requests.get(
                    f"{url}/api/dashboards/uid/{uid}",
                    headers={"Authorization": f"Bearer {GRAFANA_API_KEY}"})
                dashboard_info = dashboard_info_response.json()
                title = dashboard_info["dashboard"]["title"]
                if "hp2cdt - " in title:
                    name_of_deployment = title.split("hp2cdt - ")[1].strip()
                else:
                    name_of_deployment = title
                print(
                    f"Exporting dashboard with UID: {uid} to file: {name_of_deployment}.json", flush=True)
                with open(f"dashboards/{name_of_deployment}.json", 'w') as f:
                    json.dump(dashboard_info, f)
        except RequestException as _:
            print("Error requesting to url: ", url, flush=True)

    if not in_docker: os.chdir("..")
    return edges_info, deployment_name, grafana_url, server_port, server_url


def check_changes(edges_info):
    changed = False
    edges_data = json.loads(edges_info)
    for edge, edge_info in edges_data.items():
        if edge_info["modified"]:
            changed = True
            """edge_model = Edge.objects.get(name=edge)
            if not edge_info["is_available"]:
                edge_model.show = False
                edge_model.save()
                devices_all = Device.objects.filter(edge=edge_model)
                for device_model in devices_all:
                    device_model.show = False
                    device_model.save()
                continue
            for device, device_info in edge_info["info"].items():
                device_model = Device.objects.get(edge=edge_model, name=device)
                if not device_info["is_available"]:
                    device_model.show = False
                else:
                    device_model.show = True
                device_model.save()"""

    return changed


def get_deployment_info(setup_data):
    grafana_ip = setup_data["grafana"]["ip"]
    grafana_port = setup_data["grafana"]["port"]
    grafana_url = f'{grafana_ip}:{grafana_port}'
    server_ip = setup_data["server"]["ip"]
    server_port = setup_data["server"]["port"]
    server_url = f"http://{server_ip}:{server_port}"
    return grafana_url, server_port, server_url
