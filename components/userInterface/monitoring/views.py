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


def get_panel_link(edge_name, device_name):
    return "http://localhost:3000/d-solo/bb7d6150-0f89-471f-9589-129837044889/hp2c-dt?orgId=1&theme=light&panelId=1"


def create_deployments():
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
                            panel_link=panel_link)


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
        {"device": device}
    )
