import os
import json
import requests
from requests import RequestException

from home.dashboard import get_alerts_list, create_alert_rule_json, \
    post_alert_rules, create_folder
from home.models import Edge, Device, Deployment
from scripts.create_json_dashboard import ui_exec


def dashboard_has_changed(server_url):
    return False


def update_dashboards():
    setup_file = os.getenv("DEPLOYMENT_JSON")
    deployment_name = os.getenv("DEPLOYMENT_NAME")

    if os.path.exists(setup_file):
        with open(setup_file, 'r') as f:
            json_data = f.read()
            setup_data = json.loads(json_data)
    if not setup_data:
        print(f"Deployment setup not found in {setup_file}", flush=True)
        exit(1)

    if not os.path.exists("/.dockerenv"):
        os.chdir("scripts")

    grafana_url, server_port, server_url = get_deployment_info(setup_data)

    edges_info = None
    server_responded = False
    try:
        response = requests.get(f"{server_url}/getEdgesInfo")
        edges_info = response.text
        server_responded = True
    except RequestException as _:
        try:
            if "LOCAL_IP" in os.environ:
                server_ip = os.getenv("LOCAL_IP")
                server_url = f"http://{server_ip}:{server_port}"
                response = requests.get(f"{server_url}/getEdgesInfo")
                edges_info = response.text
                server_responded = True
        except RequestException as _:
            os.chdir("..")
            return None, deployment_name, grafana_url, None, None

    if not server_responded:
        print(f"Server {server_url} doesn't respond. Is it running?", flush=True)
        os.chdir("..")
        return None, deployment_name, grafana_url, None, None

    if edges_info is None:
        print("Edges_info is empty", flush=True)
        os.chdir("..")
        return None, deployment_name, grafana_url, None, None

    # If the method is running for the first time we must update the dashboard.
    # Otherwise, we will check if the dashboard has changed and, if it didn´t,
    # we will not update it.
    initial_execution = os.getenv("INITIAL_EXECUTION")
    if initial_execution is not None:
        initial_execution = int(initial_execution)
        if initial_execution == 0:
            changed = check_changes(edges_info)
            if not changed:
                if not os.path.exists("/.dockerenv"):
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
    grafana_api_keys = []
    try:
        gap = config_data["grafana"]["api_key"]
    except KeyError:
        print("Grafana API key not specified in config.json")
        exit(1)

    if isinstance(gap, list):
        grafana_api_keys = gap
    elif isinstance(gap, str):
        grafana_api_keys.append(gap)

    DATABASE_USERNAME = config_data["database"]["username"]
    DATABASE_PASSWORD = config_data["database"]["password"]
    DATABASE_PORT = setup_data['database']['port']

    # Define a list of GRAFANA_URLs
    URLs = [GRAFANA_URL]
    # Check if LOCAL_IP is not empty and add it to the list
    LOCAL_IP = os.getenv("LOCAL_IP", None)
    if LOCAL_IP:
        URLs.append(f"http://{LOCAL_IP}:{GRAFANA_PORT}")

    ############################ GET DATASOURCE UID ######################################
    print("Getting datasource UID...")
    datasource_uid = ""
    grafana_connected = False
    GRAFANA_API_KEY = None

    for url in URLs:
        if grafana_connected:
            break
        print(f"Trying URL {url} to handle datasource UID...", flush=True)
        for api_key in grafana_api_keys:
            try:
                response = requests.get(f"{url}/api/datasources", headers={
                    "Authorization": f"Bearer {api_key}"})
                data = response.json()
                for item in data:
                    if item["name"] == "influxdb":
                        datasource_uid = item["uid"]
                        response = requests.delete(
                            f"{url}/api/datasources/uid/{datasource_uid}",
                            headers={"Authorization": f"Bearer {api_key}"})
                        print(f"Delete response {response.text}", flush=True)
                        GRAFANA_API_KEY = api_key
                        grafana_connected = True
                        break
                if datasource_uid:
                    GRAFANA_API_KEY = api_key
                    URL = url
                    grafana_connected = True
                    break
            except RequestException as _:
                print("Error requesting to url: ", url, flush=True)
            except Exception as e:
                print(e)

    if not GRAFANA_API_KEY:
        print("Wrong Grafana API key, or Grafana-server is not reachable")
        exit(1)

    ############################ CREATE DATASOURCE ############################
    print("Creating influxdb datasource...")
    INFLUXDB_JSON = {
        "name": "influxdb",
        "type": "influxdb",
        "typeName": "InfluxDB",
        "typeLogoUrl": "/public/app/plugins/datasource/influxdb/img/influxdb_logo.svg",
        "access": "proxy",
        "url": f"http://{database_ip}:{DATABASE_PORT}",
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

    ########################### CREATE ALERT JSON #######################################
    # Create folder
    folder_uid = create_folder(URLs, GRAFANA_API_KEY)

    alerts_list = get_alerts_list(server_url, server_port)
    if alerts_list is not None:
        alert_rules = [
            create_alert_rule_json(alarm, edge, device, datasource_uid,
                                   folder_uid)
            for alarm, edge, device in alerts_list
        ]
        post_alert_rules(alert_rules, URLs, grafana_api_keys)

    ########################### CREATE JSON DASHBOARD ####################################
    print("Creating or updating dashboard...")
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
    print("Getting dashboards...")
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

    if not os.path.exists("/.dockerenv"):
        os.chdir("..")
    return edges_info, deployment_name, grafana_url, server_port, server_url


def check_changes(edges_info):
    """
    Compare edges_info with stored edges in the database and return True if changes are found
    or if any edge is marked as modified.

    :param edges_info: JSON string containing the current edge information.
    :return: Boolean indicating if any edge is modified or has changed compared to stored values.
    """
    edges_data = json.loads(edges_info)
    changed = False

    for edge_name, edge_info in edges_data.items():
        try:
            edge_model = Edge.objects.get(name=edge_name)

            if edge_info["modified"]:
                return True

            if edge_info["is_available"] != edge_model.show:
                return True

            stored_devices = Device.objects.filter(edge=edge_model)
            stored_devices_dict = {device.name: {
                "size": device.size,
                "isActionable": device.is_actionable,
                "type": device.type,
                "is_available": device.show,
                "categories": device.categories if device.is_categorical else None
            } for device in stored_devices}

            current_devices = {
                device_name: {k: v for k, v in device_info.items()
                              if k == "size" or k == "isActionable"
                              or k == "type" or k == "is_available"
                              or k == "categories"}
                for device_name, device_info in edge_info["info"].items()
            }

            if set(current_devices.keys()) != set(stored_devices_dict.keys()):
                return True

            for device_name, device_info in current_devices.items():
                if device_name not in stored_devices_dict:
                    return True

                if stored_devices_dict[device_name] != device_info:
                    return True

        except Edge.DoesNotExist:
            return True

    return changed


def get_deployment_info(setup_data):
    grafana_ip = setup_data["grafana"]["ip"]
    grafana_port = setup_data["grafana"]["port"]
    grafana_url = f'{grafana_ip}:{grafana_port}'
    server_ip = setup_data["server"]["ip"]
    server_port = setup_data["server"]["port"]
    server_url = f"http://{server_ip}:{server_port}"
    return grafana_url, server_port, server_url
