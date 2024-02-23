import os
import json

from django.shortcuts import render
from .models import Edge, Device, Deployment


def deployment_list(request):
    create_deployments()
    deployments = Deployment.objects.all()
    return render(
        request, "monitoring/deployment_list.html",
        {"deployments": deployments})


def edge_list(request, deployment_name):
    deployment = Deployment.objects.get(name=deployment_name)
    edges = Edge.objects.filter(deployment=deployment)
    return render(
        request, "monitoring/edge_list.html",
        {"edges": edges,
         "deployment_name": deployment_name})


def device_list(request, deployment_name, edge_name):
    deployment = Deployment.objects.get(name=deployment_name)
    edge = Edge.objects.get(name=edge_name, deployment=deployment)
    devices = Device.objects.filter(edge=edge)
    return render(
        request, "monitoring/device_list.html",
        {"edge": edge, "devices": devices, "deployment_name": deployment_name}
    )


def display_panel(request, deployment_name, edge_name, device_name):
    deployment = Deployment.objects.get(name=deployment_name)
    edge = Edge.objects.get(name=edge_name, deployment=deployment)
    device = Device.objects.get(name=device_name, edge=edge)
    return render(
        request, "monitoring/display_panel.html",
        {"panel_link": device.panel_link}
    )


def create_deployments():
    # Get all deployments from the database
    deployments_dir = "../../deployments"

    for deployment_name in os.listdir(deployments_dir):
        if deployment_name == "defaults" or deployment_name == "9-buses":
            continue
        # Directory path where JSON files are located
        dashboard_dir = "scripts/dashboards/"
        # Build the path to the JSON file corresponding to the deployment
        dashboard_file_path = os.path.join(dashboard_dir, f"{deployment_name}.json")
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
            print(f"JSON file not found for deployment: {deployment.name}")


def create_edges_devices(deployment, panels):
    for index, panel in enumerate(panels):
        # Get the name of the edge and the device from the panel title
        title_parts = panel['title'].split(' - ')
        edge_name = title_parts[0]
        device_name = title_parts[1]
        edge, created = Edge.objects.get_or_create(name=edge_name,
                                                   deployment=deployment)

        dashboard_name = deployment.dashboard_name.replace(" ", "")
        panel_id = index + 1
        panel_link = (f"http://localhost:3000/d-solo/{deployment.uid}/"
                      f"{dashboard_name}?orgId=1&refresh=5s&theme=light"
                      f"&panelId={panel_id}")

        device, _ = Device.objects.get_or_create(
            name=device_name,
            edge=edge,
            panel_link=panel_link
        )
        if created:
            print(f"Created edge: {edge}")


"""def create_deployments():
    deployments_dir = "../../deployments"
    for deployment_name in os.listdir(deployments_dir):
        deployment_dir = os.path.join(deployments_dir, deployment_name, "setup")
        deployment, _ = Deployment.objects.get_or_create(name=deployment_name)
        for edge_file_name in os.listdir(deployment_dir):
            create_edges_and_devices(deployment, deployment_dir, edge_file_name)


def create_edges_and_devices(deployment, deployment_dir, edge_file_name):
    if edge_file_name.endswith('.json'):
        edge_file_path = os.path.join(deployment_dir, edge_file_name)
        with open(edge_file_path, 'r') as f:
            edge_data = json.load(f)
            edge, _ = Edge.objects.get_or_create(
                    name=edge_data['global-properties']['label'],
                    deployment=deployment)
            for d in edge_data['devices']:
                panel_link = get_panel_link(edge.name, d['label'])
                device, _ = Device.objects.get_or_create(
                            name=d['label'],
                            edge=edge,
                            panel_link=panel_link)"""