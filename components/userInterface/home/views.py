import os
from requests import RequestException
from django.views.decorators.csrf import csrf_exempt
from .models import *
from django.contrib import messages


"""
def index(request):
  #create_deployments()
  get_deployment()
  deployment = Deployment.objects.get(name="testbed")
  edges = Edge.objects.filter(deployment=deployment)
  edgeDevices = {}
  for edge in edges:
    edgeDevices[edge] = Device.objects.filter(edge=edge)
    print(edgeDevices[edge], flush=True)

  context = {
    'segment'  : 'index',
    "edgeDevices": edgeDevices
    #'products' : Product.objects.all()
  }
  return render(request, "pages/index.html", context)
"""




from django.shortcuts import render
from django.http import HttpResponse
from .forms import CategoricalDeviceForm, NonCategoricalDeviceForm
import json
import requests

@csrf_exempt
def index(request):
    if request.method == 'POST':
        form_data = request.POST.dict()
        device_id = form_data.pop('device_id')
        device = Device.objects.get(id=device_id)

        values = [form_data[f"phase_{i}"] for i in range(1, device.size + 1)]

        actuation_data = {
            'values': values,
            'edgeLabel': device.edge.name,
            'actuatorLabel': device.name
        }
        response = requests.post(f"{device.edge.deployment.server_url}/actuate", json=actuation_data)

        if response.status_code == 200:
            return HttpResponse('Actuation sent correctly')
        else:
            return HttpResponse('Error while sending actuation', status=response.status_code)
    else:
        get_deployment()
        deployment_name = "testbed"
        if "DEPLOYMENT_NAME" in os.environ: deployment_name = os.getenv("DEPLOYMENT_NAME")
        deployment = Deployment.objects.get(name=deployment_name)
        edges = Edge.objects.filter(deployment=deployment)
        edgeDevices = {}
        forms = []
        for edge in edges:
            devices = Device.objects.filter(edge=edge)
            edgeDevices[edge] = devices
            for device in devices:
                form = None
                if device.is_actionable and device.is_categorical:
                    form = CategoricalDeviceForm(device)
                elif device.is_actionable:
                    form = NonCategoricalDeviceForm(device)
                forms.append((device, form))
        script_content = geomap()
        return render(request, 'pages/index.html',
                      {'edgeDevices': edgeDevices,'forms': forms, 'script_content': script_content})


import os
import json


def geomap():
    deployment_name, grafana_url, server_port, server_url = get_deployment_info()
    try:
        response = requests.get(f"{server_url}/getEdgesInfo")
        edges_info = response.json()
    except RequestException as e:
        if "LOCAL_IP" in os.environ:
            server_ip = os.getenv("LOCAL_IP")
            server_url = f"http://{server_ip}:{server_port}"
            response = requests.get(f"{server_url}/getEdgesInfo")
            edges_info = response.json()
    nodes, links = generate_nodes_and_links(edges_info)

    script_content = f"""
    const svg = d3.select("#map-container")
        .attr("width", window.innerWidth*2)
        .attr("height", window.innerHeight);

    const svgWidth = +svg.attr("width");
    const svgHeight = +svg.attr("height");

    const projection = d3.geoNaturalEarth1()
        .scale(5000)
        .translate([svgWidth/5.5, svgHeight * 4.15]);

    const path = d3.geoPath()
        .projection(projection);

    const zoom = d3.zoom()
        .scaleExtent([1, 20])
        .on("zoom", zoomed);

    svg.call(zoom);

    function zoomed(event) {{
        svg.selectAll("path").attr("transform", event.transform);
        svg.selectAll("line").attr("transform", event.transform);
        svg.selectAll("circle").attr("transform", event.transform);
    }}

    const nodes = {json.dumps(nodes)};
    const links = {json.dumps(links)};

    d3.json("../../static/maps/spain.json").then(function(world) {{
        const geojson = topojson.feature(world, world.objects.autonomous_regions);

        svg.append("path")
            .datum(geojson)
            .attr("class", "land")
            .attr("d", path);

        svg.append("path")
            .datum(topojson.mesh(world, world.objects.autonomous_regions, (a, b) => a !== b))
            .attr("class", "boundary")
            .attr("d", path);

        svg.selectAll(".link")
            .data(links)
            .enter().append("line")
            .attr("class", "link")
            .attr("x1", d => projection(nodes.find(n => n.id === d.source).coordinates)[0])
            .attr("y1", d => projection(nodes.find(n => n.id === d.source).coordinates)[1])
            .attr("x2", d => projection(nodes.find(n => n.id === d.target).coordinates)[0])
            .attr("y2", d => projection(nodes.find(n => n.id === d.target).coordinates)[1])
            .style("stroke-width", 2);

        svg.selectAll(".node")
            .data(nodes)
            .enter().append("circle")
            .attr("class", "node")
            .attr("cx", d => projection(d.coordinates)[0])
            .attr("cy", d => projection(d.coordinates)[1])
            .attr("r", 3);
    }});
    """
    return script_content


def generate_nodes_and_links(edges_info):
    nodes = []
    links = []
    for edge_id, edge_data in edges_info.items():
        x = edge_data["position"]["x"]
        y = edge_data["position"]["y"]
        nodes.append({"id": edge_id, "coordinates": [x, y]})
        for connection in edge_data["connexions"]:
            links.append({"source": edge_id, "target": connection})
    return nodes, links


def get_deployment():
    deployment_name, grafana_url, server_port, server_url = get_deployment_info()
    try:
        response = requests.get(f"{server_url}/getDevicesInfo")
        devices_info = response.text
    except RequestException as e:
        if "LOCAL_IP" in os.environ:
            server_ip = os.getenv("LOCAL_IP")
            server_url = f"http://{server_ip}:{server_port}"
            response = requests.get(f"{server_url}/getDevicesInfo")
            devices_info = response.text

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
            name=deployment_name,
            uid=dashboard_data['dashboard']['uid'],
            dashboard_name=dashboard_data['dashboard']['title'],
            server_url=server_url)
        panels = dashboard_data['dashboard']['panels']
        # Create instances of Edge and Device
        get_devices(deployment, panels, devices_info, grafana_url)


def get_deployment_info():
    deployment_name = "testbed"
    if "DEPLOYMENT_NAME" in os.environ:
        deployment_name = os.getenv("DEPLOYMENT_NAME")
    setup_file = f"../../deployments/{deployment_name}/deployment_setup.json"
    if not os.path.exists(setup_file):
        setup_file = "/data/deployment_setup.json"
    if os.path.exists(setup_file):
        with open(setup_file, 'r') as f:
            json_data = f.read()
        # Parse the JSON

        setup_data = json.loads(json_data)
        grafana_ip = setup_data["grafana"]["ip"]
        grafana_port = setup_data["grafana"]["port"]
        grafana_url = grafana_ip + ":" + grafana_port
        server_ip = setup_data["server"]["ip"]
        server_port = setup_data["server"]["port"]
        server_url = f"http://{server_ip}:{server_port}"
    return deployment_name, grafana_url, server_port, server_url


def get_devices(deployment_model, panels, devices_info, grafana_url):
    devices_data = json.loads(devices_info)
    for edge, devices in devices_data.items():
        edge_model, created = Edge.objects.get_or_create(name=edge,
                                                   deployment=deployment_model)

        for device, attributes in devices.items():
            device_model, _ = Device.objects.get_or_create(
                name=device,
                edge=edge_model,
            )
            table_link, timeseries_link = (
                get_panel_links(deployment_model, edge, device, panels, grafana_url))
            device_model.table_link = table_link
            device_model.timeseries_link = timeseries_link
            if attributes["isActionable"]:
                device_model.is_actionable = True
                device_model.size = int(attributes["size"])
                if attributes["isCategorical"]:
                    device_model.is_categorical = True
                    device_model.categories = attributes["categories"]
                else:
                    device_model.is_categorical = False
            device_model.save()
            print("Device attributes:")
            print(f"Name: {device_model.name}")
            print(f"Edge: {device_model.edge}")
            print(f"Timeseries link: {device_model.timeseries_link}")
            print(f"Table link: {device_model.table_link}")
            print(f"Is actionable: {device_model.is_actionable}")
            print(f"Size: {device_model.size}")
            print(f"Is categorical: {device_model.is_categorical}")
            print(f"Categories: {device_model.categories}")
            print()
            print()



def get_panel_links(deployment, edge, device, panels, grafana_url):
    dashboard_name = deployment.dashboard_name.replace(" ", "")
    table_link = None
    timeseries_link = None
    for index, panel in enumerate(panels):
        panel_id = index + 1
        # Get the name of the edge and the device from the panel title
        title_parts = panel['title'].split(' - ')
        edge_name = title_parts[0].replace("-", "").replace(" ", "")
        device_name = title_parts[1].replace("-", "").replace(" ","")
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
                timeseries_link = (f"http://{grafana_url}/d-solo/{deployment.uid}/"
                              f"{dashboard_name}?orgId=1&refresh=5s&theme=light"
                              f"&panelId={panel_id}")
    return table_link, timeseries_link


@csrf_exempt
def device_detail(request, edge_name, device_name):
    if request.method == 'POST':
        form_data = request.POST.dict()
        device_id = form_data.pop('device_id')
        device = Device.objects.get(id=device_id)

        values = [form_data[f"phase_{i}"] for i in range(1, device.size + 1)]
        values = ['null' if value == '' else value for value in values]
        actuation_data = {
            'values': values,
            'edgeLabel': device.edge.name,
            'actuatorLabel': device.name
        }

        response = requests.post(
            f"{device.edge.deployment.server_url}/actuate", json=actuation_data)

        if response.status_code == 200:
            messages.success(request, response.text)
        elif response.status_code == 400:
            messages.warning(request, response.text)
        else:
            messages.error(request, f"Http error: {response.status_code}")

    deployment = Deployment.objects.get(name="testbed")
    edge = Edge.objects.get(name=edge_name, deployment=deployment)
    device = Device.objects.get(name=device_name, edge=edge)

    form = None
    if device.is_actionable and device.is_categorical:
        form = CategoricalDeviceForm(device)
    elif device.is_actionable:
        form = NonCategoricalDeviceForm(device)


    return render(request, "pages/device_detail.html", {
        "device_name": device_name,
        "timeseries_link": device.timeseries_link,
        "table_link": device.table_link,
        "edge_name": edge_name,
        "form": form
    })


def tables(request):
  context = {
    'segment': 'tables'
  }
  return render(request, "pages/dynamic-tables.html", context)