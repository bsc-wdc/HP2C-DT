"This module contains the methods used for creating and displaying the dashboard, including showing device states and rendering edges on a geomap."
import ast
import re
import json
import os

import requests
from requests import RequestException

from home.models import Edge, Device, Deployment
from home.utils import parse_string_to_list


def geomap(server_port, server_url):
    """Generate a script for rendering a geographic map with the edges' main
    information using D3.js

    :param server_port: The port number of the server.
    :param server_url: The URL of the server.
    :return: The JavaScript code for rendering the map.
    """
    geo_info = {}
    if server_url:
        try:
            response = requests.get(f"{server_url}/getGeoInfo")
            geo_info = response.json()
        except RequestException as _:
            if "LOCAL_IP" in os.environ:
                server_ip = os.getenv("LOCAL_IP")
                server_url = f"http://{server_ip}:{server_port}"
                response = requests.get(f"{server_url}/getGeoInfo")
                geo_info = response.json()
    nodes, links = generate_nodes_and_links(geo_info)

    script_content = f"""
            const svg = d3.select("#map-container")
                .attr("width", window.innerWidth * 2)
                .attr("height", window.innerHeight);

            const svgWidth = +svg.attr("width");
            const svgHeight = +svg.attr("height");

            const projection = d3.geoNaturalEarth1()
                .scale(5000)
                .translate([svgWidth / 4.25, svgHeight * 4.15]);

            const path = d3.geoPath()
                .projection(projection);

            const zoom = d3.zoom()
                .scaleExtent([1, 20])
                .on("zoom", zoomed);

            svg.call(zoom);

            const g = svg.append("g");

            function zoomed(event) {{
                g.attr("transform", event.transform);
            }}

            const nodes = {json.dumps(nodes)};
            const links = {json.dumps(links)};

            d3.json("../../static/maps/spain.json").then(function(world) {{
                const geojson = topojson.feature(world, world.objects.autonomous_regions);

                g.append("path")
                    .datum(geojson)
                    .attr("class", "land")
                    .attr("d", path);

                g.append("path")
                    .datum(topojson.mesh(world, world.objects.autonomous_regions, (a, b) => a !== b))
                    .attr("class", "boundary")
                    .attr("d", path);

                const linkGroup = g.append("g")
                    .attr("class", "links");

                linkGroup.selectAll(".link")
                    .data(links)
                    .enter().append("line")
                    .attr("class", "link")
                    .attr("x1", d => projection(nodes.find(n => n.id === d.source).coordinates)[0])
                    .attr("y1", d => projection(nodes.find(n => n.id === d.source).coordinates)[1])
                    .attr("x2", d => projection(nodes.find(n => n.id === d.target).coordinates)[0])
                    .attr("y2", d => projection(nodes.find(n => n.id === d.target).coordinates)[1])
                    .style("stroke-width", 2);

                const nodeGroup = g
                    .attr("class", "nodes");

                nodeGroup.selectAll(".node-group")
                    .data(nodes)
                    .enter().append("g")
                    .attr("class", "node-group")
                    .attr("transform", d => `translate(${{projection(d.coordinates)[0]}}, ${{projection(d.coordinates)[1]}})`)
                    .each(function(d) {{

                        const group = d3.select(this);

                        const circle = group.append("circle")
                            .attr("r", 3)
                            .attr("fill", d => {{
                                if (d.show === 0) return "red";
                                else if (d.show === 1) return "orange";
                                else return "black";
                            }})
                            .on("click", function() {{
                                window.location.href = `/${{d.id}}`;   
                                d3.event.stopPropagation(); 
                            }});

                    const rectHeight = 20; 
                    const rect = group.append("rect")
                        .attr("class", "node-rect")
                        .attr("x", 5) 
                        .attr("y", -20) 
                        .attr("height", rectHeight)
                        .attr("rx", 5) 
                        .attr("ry", 5); 

                    const text = group.append("text")
                        .attr("class", "node-label")
                        .attr("x", 10) 
                        .attr("y", -5)
                        .text(d => d.id);

                    const textWidth = text.node().getBBox().width;
                    rect.attr("width", textWidth + 10);
                }});
            }});
        """
    return script_content


def generate_nodes_and_links(edges_info):
    """
    Generates nodes and links data from the provided edges information.

    :param edges_info: Information about the edges.
    :return: A tuple containing lists of nodes and links.
    """
    nodes = []
    links = []
    for edge_id, edge_data in edges_info.items():
        x = edge_data["position"]["x"]
        y = edge_data["position"]["y"]
        edge = Edge.objects.get(name=edge_id)
        show = 0
        if edge_data["show"]:
            show = 2
            devices = Device.objects.filter(edge=edge)
            for device in devices:
                if not device.show:
                    show = 1
                    break

        nodes.append({"id": edge_id, "coordinates": [x, y], "show": show})
        for connection in edge_data["connections"]:
            if Edge.objects.filter(
                    name=edge_id).exists() and Edge.objects.filter(
                name=connection).exists():
                links.append({"source": edge_id, "target": connection})
    return nodes, links


def get_deployment(edges_info, deployment_name, grafana_url, server_url,
                   server_port):
    """
    Retrieves deployment information and devices from server, and creates the
    deployment model.

    :param edges_info: Information about the devices.
    :param deployment_name: The name of the deployment.
    :param grafana_url: The URL of Grafana.
    :param server_url: The URL of the server.
    :param server_port: Port of the server
    """

    # Directory path where JSON files are located
    dashboard_dir = "../../components/userInterface/scripts/dashboards/"
    if not os.path.exists(dashboard_dir):
        # Path to dashboards in docker
        dashboard_dir = "/app/scripts/dashboards"

    dashboard_file_path = os.path.join(dashboard_dir,
                                       f"{deployment_name}.json")
    if os.path.exists(dashboard_file_path):
        with open(dashboard_file_path, 'r') as f:
            json_data = f.read()
        # Parse the JSON
        dashboard_data = json.loads(json_data)

        deployment, _ = Deployment.objects.get_or_create(
            name=deployment_name)
        deployment.uid = dashboard_data['dashboard']['uid']
        deployment.dashboard_name = dashboard_data['dashboard']['title']
        deployment.server_url = server_url
        deployment.save()

        panels = dashboard_data['dashboard']['panels']
        dashboard_name = deployment.dashboard_name.replace("-", "").replace(" ", "")
        deployment.alerts_link = get_alerts_link(panels, grafana_url,
                                                 deployment, dashboard_name)
        deployment.save()
        # Create instances of Edge and Device
        get_devices(deployment, panels, edges_info, grafana_url)
        try:
            response = requests.post(f"{server_url}/setUpdated")
        except RequestException as _:
            if "LOCAL_IP" in os.environ:
                server_ip = os.getenv("LOCAL_IP")
                server_url = f"http://{server_ip}:{server_port}"
                response = requests.post(f"{server_url}/setUpdated")


def get_devices(deployment_model, panels, edges_info, grafana_url):
    """
        Retrieves device information from the server and saves it to the
        database (as models).

        :param deployment_model: The deployment model instance.
        :param panels: List of panel data.
        :param edges_info: Information about the devices.
        :param grafana_url: The URL of Grafana.
        """
    Edge.objects.all().delete()
    edges_data = json.loads(edges_info)
    for edge, edge_info in edges_data.items():
        edge_available = edge_info["is_available"]
        edge_model, created = Edge.objects.get_or_create(
            name=edge,
            deployment=deployment_model)
        edge_model.show = edge_available
        edge_model.save()

        for device, attributes in edge_info["info"].items():
            device_available = attributes["is_available"]
            device_type = attributes["type"]
            device_model, _ = Device.objects.get_or_create(
                name=device,
                edge=edge_model
            )
            device_model.show = device_available
            device_model.type = device_type
            device_model.save()
            table_link, timeseries_link = (
                get_panel_links(deployment_model, edge, device, panels,
                                grafana_url))
            device_model.table_link = table_link
            device_model.timeseries_link = timeseries_link
            device_model.size = int(attributes["size"])
            if attributes["isActionable"]:
                device_model.is_actionable = True
                if attributes["isCategorical"]:
                    device_model.is_categorical = True
                    device_model.categories = attributes["categories"]
                else:
                    device_model.is_categorical = False
            device_model.save()


def get_panel_links(deployment, edge, device, panels, grafana_url):
    """Retrieves links to panels from Grafana API Rest.

    :param deployment: The deployment model instance.
    :param edge: The name of the edge.
    :param device: The name of the device.
    :param panels: List of panel data.
    :param grafana_url: The URL of Grafana.
    :return: A tuple containing links to the table and time series panels.
    """
    dashboard_name = deployment.dashboard_name.replace(" ", "")
    table_link = None
    timeseries_link = None
    for index, panel in enumerate(panels):
        title = panel['title']
        if title != "Alerts":
            panel_id = index + 1
            # Get the name of the edge and the device from the panel title
            title_parts = title.split(' - ')
            edge_name = title_parts[0].replace("-", "").replace(" ", "")
            device_name = title_parts[1].replace("-", "").replace(" ", "")
            is_table = False
            if "(Table)" in edge_name:
                is_table = True
                edge_name = edge_name.replace("(Table)", "")
            if edge_name == edge and device_name == device:
                if is_table:
                    table_link = (f"http://{grafana_url}/d-solo/{deployment.uid}/"
                                  f"{dashboard_name}?orgId=1&refresh=5s&theme=light"
                                  f"&panelId={panel_id}")
                else:
                    timeseries_link = (
                        f"http://{grafana_url}/d-solo/{deployment.uid}/"
                        f"{dashboard_name}?orgId=1&refresh=5s&theme=light"
                        f"&panelId={panel_id}")

    return table_link, timeseries_link


def create_alert_rule_json(alarm_name, edge_name, device_name, datasource_uid, folder_uid):
    """
    Creates a JSON payload for creating a new alert rule in Grafana.

    Args:
        alarm_name (str): The name of the alarm.
        edge_name (str): The name of the edge (or "-" if not applicable).
        device_name (str): The name of the device (or "-" if not applicable).
        datasource_uid (str): The UID of the datasource in Grafana.
        folder_uid (str): The UID of the folder where the alert rule will be stored.

    Returns:
        dict: The JSON payload for the Grafana API.
    """
    # Define the query to check the alarm status
    query = f"SELECT \"status\" FROM alarms WHERE \"alarm\" = '{alarm_name}' AND \"edge\" = '{edge_name}' AND \"device\" = '{device_name}'"
    # Create the JSON payload
    alert_rule = {
        "title": f"{alarm_name}_{edge_name}_{device_name}",
        "ruleGroup": "API",
        "folderUID": folder_uid,
        "noDataState": "OK",
        "execErrState": "OK",
        "for": "10s",
        "orgId": 1,
        "uid": "",
        "condition": "B",
        "annotations": {
            "summary": f"Alarm triggered for {alarm_name} on edge {edge_name} and device {device_name}"
        },
        "labels": {
            "alarm": alarm_name,
            "edge": edge_name,
            "device": device_name
        },
        "data": [
            {
                "refId": "A",
                "relativeTimeRange": {
                    "from": 600,
                    "to": 0
                },
                "datasourceUid": datasource_uid,
                "model": {
                    "intervalMs": 1000,
                    "maxDataPoints": 43200,
                    "query": query,
                    "rawQuery": True,
                    "refId": "A",
                    "resultFormat": "time_series"
                }
            },
            {
                "refId": "B",
                "relativeTimeRange": {
                    "from": 600,
                    "to": 0
                },
                "datasourceUid": "__expr__",
                "model": {
                    "conditions": [
                        {
                            "evaluator": {
                                "params": [],
                                "type": "gt"
                            },
                            "operator": {
                                "type": "and"
                            },
                            "query": {
                                "params": ["B"]
                            },
                            "reducer": {
                                "params": [],
                                "type": "last"
                            },
                            "type": "query"
                        }
                    ],
                    "datasource": {
                        "type": "__expr__",
                        "uid": "__expr__"
                    },
                    "expression": "A",
                    "intervalMs": 1000,
                    "maxDataPoints": 43200,
                    "reducer": "last",
                    "refId": "B",
                    "type": "reduce"
                }
            },
            {
                "refId": "C",
                "relativeTimeRange": {
                    "from": 600,
                    "to": 0
                },
                "datasourceUid": "__expr__",
                "model": {
                    "conditions": [
                        {
                            "evaluator": {
                                "params": [0],  # Threshold value (status > 0)
                                "type": "gt"
                            },
                            "operator": {
                                "type": "and"
                            },
                            "query": {
                                "params": ["C"]
                            },
                            "reducer": {
                                "params": [],
                                "type": "last"
                            },
                            "type": "query"
                        }
                    ],
                    "datasource": {
                        "type": "__expr__",
                        "uid": "__expr__"
                    },
                    "expression": "B",
                    "intervalMs": 1000,
                    "maxDataPoints": 43200,
                    "refId": "C",
                    "type": "threshold"
                }
            }
        ]
    }
    return alert_rule


def get_alerts_list(server_url, server_port):
    alerts_list = None
    server_responded = False

    try:
        response = requests.get(f"{server_url}/getAlarms")
        alerts_list = response.text
        server_responded = True
    except RequestException:
        try:
            if "LOCAL_IP" in os.environ:
                server_ip = os.getenv("LOCAL_IP")
                server_url = f"http://{server_ip}:{server_port}"
                response = requests.get(f"{server_url}/getAlarms")
                alerts_list = response.text
                server_responded = True
        except RequestException:
            print("Error getting the alerts list")

    if not server_responded:
        print(f"Server {server_url} doesn't respond. Is it running?", flush=True)

    if alerts_list is None:
        print("Alerts list is empty", flush=True)
        return None

    try:
        print("Raw alerts list response:", alerts_list)
        alerts_list = parse_string_to_list(alerts_list)
    except (ValueError, SyntaxError):
        print("Failed to decode alerts list", flush=True)
        return None

    return alerts_list



def post_alert_rules(alert_rules, URLs, grafana_api_keys):
    """
    Posts alert rules to the Grafana API.

    Args:
        alert_rules (list): A list of JSON payloads for alert rules.
        grafana_url (str): The base URL of the Grafana instance.
        api_key (str): The Grafana API key for authentication.

    Returns:
        None
    """
    for url in URLs:
        for api_key in grafana_api_keys:
            headers = {
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json"
            }

            for rule in alert_rules:
                try:
                    response = requests.post(
                        f"{url}/api/v1/provisioning/alert-rules",
                        headers=headers,
                        json=rule
                    )

                    if response.status_code == 200:
                        print(f"Successfully created alert rule: {rule['title']}")
                    else:
                        print(f"Failed to create alert rule: {rule['title']}")
                        print(f"Response: {response.text}")
                except requests.RequestException as e:
                    print(f"Error posting alert rule {rule['title']}: {e}")


def create_folder(URLs, GRAFANA_API_KEY):
    print("Getting folder UID...")
    folder_uid = ""
    grafana_connected = False

    for url in URLs:
        if grafana_connected:
            break
        print(f"Trying URL {url} to handle folder UID...", flush=True)
        try:
            response = requests.get(f"{url}/api/folders", headers={
                "Authorization": f"Bearer {GRAFANA_API_KEY}"})
            data = response.json()
            for item in data:
                folder_uid = item["uid"]
                response = requests.delete(
                    f"{url}/api/folders/{folder_uid}?forceDeleteRules=true",
                    headers={"Authorization": f"Bearer {GRAFANA_API_KEY}"})
                grafana_connected = True

            if folder_uid:
                grafana_connected = True
                break
        except RequestException as _:
            print("Error requesting to url: ", url, flush=True)
        except Exception as e:
            print(e)

    FOLDER_JSON = {
        "title": "DTAlarms",
        "overwrite": True
    }

    folder_uid = ""
    for url in URLs:
        try:
            headers = {
                "Authorization": f"Bearer {GRAFANA_API_KEY}",
                "Content-Type": "application/json"
            }
            print("Creating folder: ", FOLDER_JSON)
            response = requests.post(f"{url}/api/folders", headers=headers,
                                     json=FOLDER_JSON)

            if response.status_code == 200:
                response = response.json()
                folder_uid = response["uid"]
        except RequestException as _:
            print("Error requesting to url: ", url, flush=True)

    if not folder_uid:
        print("Datasource influxdb uid not found")
        exit(1)
    print("Folder uid: ", folder_uid)
    return folder_uid

def create_alerts_panel(datasource_uid):
    PANEL_JSON = {
        "type": "alertlist",
        "title": "Alerts",
        "gridPos": {
            "x": 0,
            "y": 0,
            "w": 14,
            "h": 14
        },
        "datasource": {
            "uid": f"{datasource_uid}",
            "type": "influxdb"
        },
        "options": {
            "viewMode": "list",
            "groupMode": "default",
            "groupBy": [],
            "maxItems": 20,
            "sortOrder": 1,
            "dashboardAlerts": False,
            "alertName": "",
            "alertInstanceLabelFilter": "",
            "stateFilter": {
                "firing": True,
                "pending": True,
                "noData": True,
                "normal": True,
                "error": True
            }
        }
    }
    return PANEL_JSON


def get_alerts_link(panels, grafana_url, deployment, dashboard_name):
    panel_id = 0
    for index, panel in enumerate(panels):
        title = panel['title']
        if title == "Alerts":
            panel_id = index + 1
            break

    return (f"http://{grafana_url}/d-solo/{deployment.uid}/"
            f"{dashboard_name}?orgId=1&refresh=5s&theme=light"
            f"&panelId={panel_id}")