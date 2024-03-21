import os
import json
import requests

from django.shortcuts import render, redirect
from admin_datta.forms import RegistrationForm, LoginForm, UserPasswordChangeForm, UserPasswordResetForm, UserSetPasswordForm
from django.contrib.auth.views import LoginView, PasswordChangeView, PasswordResetConfirmView, PasswordResetView
from django.views.generic import CreateView
from django.contrib.auth import logout

from django.contrib.auth.decorators import login_required
from requests import RequestException

from .models import *


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

def get_deployment():
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
        server_url = f"http://{server_ip}:{server_port}/getDevicesInfo"
        try:
            response = requests.get(server_url)
            devices_info = response.text
        except RequestException as e:
            if "LOCAL_IP" in os.environ:
                server_ip = os.getenv("LOCAL_IP")
                server_url = f"http://{server_ip}:{server_port}/getDevicesInfo"
                response = requests.get(server_url)
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
                dashboard_name=dashboard_data['dashboard']['title'])
            panels = dashboard_data['dashboard']['panels']
            # Create instances of Edge and Device
            get_devices(deployment, panels, devices_info, grafana_url)


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

def create_deployments():
    # Get all deployments from the database
    deployments_dir = "../../deployments"
    if os.path.exists(deployments_dir):
        deployments = [d for d in os.listdir(deployments_dir) if "." not in d]
    else:
        # Get environment variable (only for docker)
        deployments = [os.getenv("DEPLOYMENT_NAME")]

    for deployment_name in deployments:
        if deployment_name == "defaults" or deployment_name == "9-buses":
            continue
        # Directory path where JSON files are located
        dashboard_dir = "../../components/userInterface/scripts/dashboards/"
        if not os.path.exists(dashboard_dir):
            # Path to dashboards in docker
            dashboard_dir = "/app/scripts/dashboards"
        # Build the path to the JSON file corresponding to the deployment
        dashboard_file_path = os.path.join(dashboard_dir,
                                           f"{deployment_name}.json")
        # Check if the JSON file exists
        if os.path.exists(dashboard_file_path):
            with open(dashboard_file_path, 'r') as f:
              json_data = f.read()
            # Parse the JSON
            dashboard_data = json.loads(json_data)
            deployment, _ = Deployment.objects.get_or_create(
              name=deployment_name,
              uid=dashboard_data['dashboard']['uid'],
              dashboard_name=dashboard_data['dashboard']['title'])
            panels = dashboard_data['dashboard']['panels']
            # Create instances of Edge and Device
            create_edges_devices(deployment, panels)
        else:
          print(f"JSON file not found for deployment: {deployment_name}")


def create_edges_devices(deployment, panels):
  for index, panel in enumerate(panels):
    # Get the name of the edge and the device from the panel title
    title_parts = panel['title'].split(' - ')
    edge_name = title_parts[0]
    device_name = title_parts[1]
    is_table = False
    if "(Table)" in edge_name:
        is_table = True
        edge_name = edge_name.split(" ")[1]
    edge, created = Edge.objects.get_or_create(name=edge_name,
                                               deployment=deployment)

    dashboard_name = deployment.dashboard_name.replace(" ", "")
    device, _ = Device.objects.get_or_create(
        name=device_name,
        edge=edge,
    )
    deployment_setup_file = f"../../deployments/{deployment.name}/deployment_setup.json"
    if not os.path.exists(deployment_setup_file):
        deployment_setup_file = "/data/deployment_setup.json"
    with open(deployment_setup_file, 'r') as f:
        json_data = f.read()
        deployment_data = json.loads(json_data)
        grafana_ip = deployment_data["grafana"]["ip"]
        grafana_port = deployment_data["grafana"]["port"]
        grafana_url = grafana_ip + ":" + grafana_port

    if is_table:
        table_id = index + 1
        table_link = (f"http://{grafana_url}/d-solo/{deployment.uid}/"
                      f"{dashboard_name}?orgId=1&refresh=5s&theme=light"
                      f"&panelId={table_id}")
        device.table_link = table_link
        device.save()
    else:
        timeseries_id = index + 1
        timeseries_link = (f"http://{grafana_url}/d-solo/{deployment.uid}/"
                           f"{dashboard_name}?orgId=1&refresh=5s&theme=light"
                           f"&panelId={timeseries_id}")
        device.timeseries_link = timeseries_link
        device.save()
    if created:
      print(f"Created edge: {edge}")


def device_detail(request, edge_name, device_name):
    deployment = Deployment.objects.get(name="testbed")
    edge = Edge.objects.get(name=edge_name, deployment=deployment)
    device = Device.objects.get(name=device_name, edge=edge)
    return render(request, "pages/device_detail.html",
                { "device_name": device_name,
                "timeseries_link": device.timeseries_link,
                "table_link": device.table_link})

def tables(request):
  context = {
    'segment': 'tables'
  }
  return render(request, "pages/dynamic-tables.html", context)