import os
import json

from django.shortcuts import render, redirect
from admin_datta.forms import RegistrationForm, LoginForm, UserPasswordChangeForm, UserPasswordResetForm, UserSetPasswordForm
from django.contrib.auth.views import LoginView, PasswordChangeView, PasswordResetConfirmView, PasswordResetView
from django.views.generic import CreateView
from django.contrib.auth import logout

from django.contrib.auth.decorators import login_required

from .models import *


def index(request):
  create_deployments()
  deployment = Deployment.objects.get(name="testbed")
  edges = Edge.objects.filter(deployment=deployment)
  edgeDevices = {}
  for edge in edges:
    edgeDevices[edge] = Device.objects.filter(edge=edge)

  context = {
    'segment'  : 'index',
    "edgeDevices": edgeDevices
    #'products' : Product.objects.all()
  }
  return render(request, "pages/index.html", context)

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

def create_deployments():

    # Get all deployments from the database
    deployments_dir = "../../deployments"

    for deployment_name in os.listdir(deployments_dir):
        if deployment_name == "defaults" or deployment_name == "9-buses":
          continue
        # Directory path where JSON files are located
        dashboard_dir = "../../components/userInterface/scripts/dashboards/"
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
    print("title ", panel['title'])
    title_parts = panel['title'].split(' - ')
    edge_name = title_parts[0]
    device_name = title_parts[1]
    is_table = False
    if "(Table)" in edge_name:
        is_table = True
        print("Is table")
        edge_name = edge_name.split(" ")[1]
    print("edge name ", edge_name)
    edge, created = Edge.objects.get_or_create(name=edge_name,
                                               deployment=deployment)

    dashboard_name = deployment.dashboard_name.replace(" ", "")
    device, _ = Device.objects.get_or_create(
        name=device_name,
        edge=edge,
    )
    if is_table:
        table_id = index + 1
        print("Table id" , table_id)
        table_link = (f"http://localhost:3000/d-solo/{deployment.uid}/"
                      f"{dashboard_name}?orgId=1&refresh=5s&theme=light"
                      f"&panelId={table_id}")
        device.table_link = table_link
        device.save()
    else:
        timeseries_id = index + 1
        print("Timeseries id ", timeseries_id)
        timeseries_link = (f"http://localhost:3000/d-solo/{deployment.uid}/"
                           f"{dashboard_name}?orgId=1&refresh=5s&theme=light"
                           f"&panelId={timeseries_id}")
        device.timeseries_link = timeseries_link
        device.save()
    print("")
    print("")
    if created:
      print(f"Created edge: {edge}")