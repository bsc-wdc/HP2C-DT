import os
import random
import re
import shlex
import string
import subprocess
import threading
import time
import uuid

import pandas as pd
import yaml

from datetime import datetime
from stat import S_ISDIR
from django.contrib.auth import authenticate, login, logout
from requests import RequestException
from django.views.decorators.csrf import csrf_exempt
from django.core.exceptions import ObjectDoesNotExist
from scripts.update_dashboards import update_dashboards, get_deployment_info
from .models import *
from django.contrib import messages
from django.shortcuts import render, redirect, get_object_or_404
from django.http import HttpResponse, Http404
from .forms import (CategoricalDeviceForm, NonCategoricalDeviceForm, \
    CreateUserForm, Machine_Form, Key_Gen_Form, DocumentForm, ExecutionForm,
                    CreateToolForm)
from django.forms import formset_factory
import json
import requests
from django.contrib.auth.decorators import login_required
from cryptography.fernet import Fernet
import paramiko
from io import StringIO
from django.db.models import Q
from django import forms


@login_required
def edge_detail(request, edge_name):
    edgeDevices = {}
    forms = []
    try:
        edge = Edge.objects.get(name=edge_name)
        devices = Device.objects.filter(edge=edge)
        edgeDevices[edge] = devices
        for device in devices:
            form = None
            if device.is_actionable and device.is_categorical:
                form = CategoricalDeviceForm(device)
            elif device.is_actionable:
                form = NonCategoricalDeviceForm(device)
            forms.append((device, form))
    except:
        pass
    return render(request, 'pages/edge_detail.html',
                  {'edgeDevices': edgeDevices, 'forms': forms})


@login_required
@csrf_exempt
def index(request):
    """Render the index page.

    Displays the index page of the application with forms for submitting device data.

    :param request: The HTTP request object.
    :return: The HTTP response object.
    """
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
        response = requests.post(
            f"{device.edge.deployment.server_url}/actuate",
            json=actuation_data)

        if response.status_code == 200:
            return HttpResponse('Actuation sent correctly')
        else:
            return HttpResponse('Error while sending actuation',
                                status=response.status_code)
    else:
        edges_info, deployment_name, grafana_url, server_port, server_url = (
            update_dashboards())
        if edges_info is not None:
            get_deployment(edges_info, deployment_name, grafana_url,
                           server_url, server_port)
        edgeDevices = {}
        forms = []
        try:
            deployment = Deployment.objects.get(name=deployment_name)
            edges = Edge.objects.filter(deployment=deployment)
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
        except:
            pass
        script_content = geomap(server_port, server_url)
        return render(request, 'pages/index.html',
                      {'edgeDevices': edgeDevices, 'forms': forms,
                       'script_content': script_content})


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
            name=deployment_name,
            uid=dashboard_data['dashboard']['uid'],
            dashboard_name=dashboard_data['dashboard']['title'],
            server_url=server_url)
        panels = dashboard_data['dashboard']['panels']
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
            if attributes["isActionable"]:
                device_model.is_actionable = True
                device_model.size = int(attributes["size"])
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
        panel_id = index + 1
        # Get the name of the edge and the device from the panel title
        title_parts = panel['title'].split(' - ')
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


@csrf_exempt
def device_detail(request, edge_name, device_name):
    """
    Displays the detail page of a device, including form for submitting device
    data.

    :param request: The HTTP request object.
    :param edge_name: The name of the edge.
    :param device_name: The name of the device.
    :return: The HTTP response object.
    """
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
            f"{device.edge.deployment.server_url}/actuate",
            json=actuation_data)

        if response.status_code == 200:
            messages.success(request, response.text)
        elif response.status_code == 400:
            messages.warning(request, response.text)
        else:
            messages.error(request, f"Http error: {response.status_code}")

    edge = Edge.objects.get(name=edge_name)
    device = Device.objects.get(name=device_name, edge=edge)

    form = None
    if device.is_actionable and device.is_categorical:
        form = CategoricalDeviceForm(device)
    elif device.is_actionable:
        form = NonCategoricalDeviceForm(device)

    return render(request, "pages/device_detail.html", {
        "device": device,
        "device_name": device_name,
        "timeseries_link": device.timeseries_link,
        "table_link": device.table_link,
        "edge_name": edge_name,
        "form": form
    })


#################### MACHINES ######################
@login_required
def new_machine(request):
    if request.method == 'POST':
        form = Machine_Form(request.POST)
        form.data = form.data.copy()
        form.data['author'] = request.user

        if form.is_valid():
            user = form.cleaned_data.get('user')
            fqdn = form.cleaned_data.get('fqdn')
            author = form.cleaned_data.get('author')

            if Machine.objects.filter(author=author, user=user, fqdn=fqdn).exists():
                error_string = 'A machine with this author, user and FQDN already exists.'
                return render(request, 'pages/new_machine.html', {'form': form, 'error': True, 'message': error_string})

            instance = form.save(commit=False)
            instance.author = request.user
            instance.save()
            request.session['machine_created'] = f"{user}@{fqdn}"
            return redirect('hpc_machines')
        else:
            error_string = ""
            for field, errors in form.errors.items():
                error_string += f"{', '.join(errors)}\n"

            return render(request, 'pages/new_machine.html', {'form': form, 'error': True, 'message': error_string})

    else:
        form = Machine_Form(initial={'author': request.user})

    return render(request, 'pages/new_machine.html', {'form': form})


def populate_executions_machines(request):
    machine_connected = get_machine_connected(request)
    machines = Machine.objects.all().filter(author=request.user)
    machines_done = []
    if machine_connected is not None:
        machines_done.append("" + str(machine_connected.user) + "@" + machine_connected.fqdn)
    if machines.count() != 0:
        for machine in machines:
            if machine != machine_connected:
                machines_done.append("" + str(machine.user) + "@" + machine.fqdn)
    return machines_done


def get_machine_connected(request):
    connection = Connection.objects.filter(user=request.user, status="Active")
    machine_connected = None
    if len(connection) > 0:
        connection = connection[0]
        machine_connected = connection.machine
    return machine_connected


##################### USERS #########################
def login_page(request):
    if request.method == 'POST':
        username = request.POST['username']
        password = request.POST['password1']
        # if is_recaptcha_valid(request):
        user = authenticate(request, username=username, password=password)
        if user is not None:
            form = login(request, user)
            messages.success(request, f' welcome {username} !!')
            return redirect('dashboard')
        else:
            form = CreateUserForm()
            return render(request, 'pages/login_page.html',
                          {'form': form, 'error': True})
    else:
        form = CreateUserForm()
    return render(request, 'pages/login_page.html', {'form': form})


def register_page(request):
    if request.method == 'POST':
        form = CreateUserForm(request.POST)
        if form.is_valid():
            form.save()
            return redirect('login')
        else:
            error_string = ""
            for field, errors in form.errors.items():
                error_string += f"{field}: {', '.join(errors)}\n"
            for error in form.non_field_errors():
                error_string += f"Non-field error: {error}\n"

            return render(request, 'pages/register_page.html',
                          {'form': form, 'error': True,
                           'message': error_string})
    else:
        form = CreateUserForm()

    return render(request, 'pages/register_page.html', {'form': form})


@login_required
def logoutUser(request):
    logout(request)
    messages.info(request, "Logged out successfully!")
    return redirect("login")


######################## SSH KEYS ####################
@login_required
def ssh_keys(request):
    machine_connected = get_machine_connected(request)
    if request.method == 'POST':
        form = Key_Gen_Form(request.POST)
        if form.is_valid():
            Connection.objects.filter(user=request.user).update(status="Disconnect")
            if 'reuse_token_button' in request.POST:  # if the user has more than 1 Machine, he can decide to use the same SSH keys and token for all its machines
                machine = request.POST.get('machineChoice')
                user = machine.split("@")[0]
                fqdn = machine.split("@")[1]
                machine_found = Machine.objects.get(author=request.user, user=user, fqdn=fqdn)
                instance = form.save(commit=False)
                instance.author = request.user
                instance.machine = machine_found
                instance.public_key = Key_Gen.objects.get(author=instance.author).public_key
                instance.private_key = Key_Gen.objects.get(author=instance.author).private_key
                instance.save()
                request.session['warning'] = "first"
                return redirect('dashboard')
            else:  # normal generation of the SSH keys
                instance = form.save(commit=False)
                instance.author = request.user
                machine = request.POST.get('machineChoice')  # it's the machine choosen by the user
                user = machine.split("@")[0]
                fqdn = machine.split("@")[1]
                request.userMachine = user
                request.fqdn = fqdn
                machine_found = Machine.objects.get(author=request.user, user=user, fqdn=fqdn)
                instance.machine = machine_found
                token = Fernet.generate_key()  # to generate a security token
                key = paramiko.RSAKey.generate(2048)  # to generate the SSH keys
                privateString = StringIO()
                key.write_private_key(privateString)
                private_key = privateString.getvalue()
                x = private_key.split("\'")
                private_key = x[0]
                public_key = key.get_base64()
                enc_private_key = encrypt(private_key.encode(), token)  # encrypting the private SSH keys using the security token, only the user is allowed to use its SSH keys to connect to its machine
                enc_private_key = str(enc_private_key).split("\'")[1]
                x = str(token).split("\'")
                token = x[1]
                instance.public_key = public_key
                instance.private_key = enc_private_key
                if Key_Gen.objects.filter(author=instance.author, machine=instance.machine).exists():
                    if request.session['warning'] == "first":
                        if (Key_Gen.objects.filter(author=instance.author).count() > 1):
                            request.session['warning'] = "third"
                            return render(request, 'pages/ssh_keys.html',
                                          {'form': form, 'warning': request.session['warning'],
                                           'machines': populate_executions_machines(request)})
                        else:
                            request.session['warning'] = "second"
                            return render(request, 'pages/ssh_keys.html',
                                          {'form': form, 'warning': request.session['warning'],
                                           'machines': populate_executions_machines(request)})

                    if (Key_Gen.objects.filter(author=instance.author).count() > 1):
                        Key_Gen.objects.filter(author=instance.author).update(public_key=instance.public_key,
                                                                              private_key=instance.private_key)

                    else:
                        Key_Gen.objects.filter(author=instance.author, machine=instance.machine).update(
                            public_key=instance.public_key, private_key=instance.private_key)
                elif (Key_Gen.objects.filter(author=instance.author).exists()):
                    if request.session['reuse_token'] == "no":
                        request.session['reuse_token'] = "yes"
                        request.session['warning'] = "first"
                        machine = request.POST.get('machineChoice')
                        return render(request, 'pages/ssh_keys.html',
                                      {'form': form, 'warning': request.session['warning'],
                                       'reuse_token': request.session['reuse_token'],
                                       'machines': populate_executions_machines(request), 'choice': machine})
                else:
                    instance.save()
                public_key = "rsa-sha2-512 " + public_key
                return render(request, 'pages/ssh_keys_generation.html', {'token': token, 'public_key': public_key})
    else:
        form = Key_Gen_Form(initial={'public_key': 123, 'private_key': 123})
        request.session['reuse_token'] = "no"
        request.session['warning'] = "first"
        if not populate_executions_machines(request):
            request.session['check_existence_machines'] = "yes"
        else:
            request.session['check_existence_machines'] = "no"

    context = {'form': form, 'warning': request.session['warning'], 'reuse_token': request.session['reuse_token'],
                   'machines': populate_executions_machines(request),
               'check_existence_machines': request.session['check_existence_machines']}

    if machine_connected is not None:
        machine_connected = "" + machine_connected.user + "@" + machine_connected.fqdn
        context['machine_connected'] = machine_connected

    return render(request, 'pages/ssh_keys.html', context)


def encrypt(message: bytes, key: bytes) -> bytes:
    return Fernet(key).encrypt(message)


def decrypt(token: bytes, key: bytes) -> bytes:
    try:
        res = Fernet(key).decrypt(token)
    except Exception as e:
        print("Error decrypting token: %s", str(e))
        raise
    return res


@login_required
def ssh_keys_generation(request):
    if request.method == 'POST':
        return render('/')
    else:
        return render('/')


def extract_tool_data(request, tool_name):
    tool_data = {}
    tool = Tool.objects.get(name=tool_name)
    tool_data['tool_name'] = tool.name
    tool_data['setup'] = {}
    tool_data['setup']['github'] = tool.repos_json()
    tool_data['slurm'] = {}
    tool_data['slurm']['modules'] = tool.get_modules_list()

    for field_key, field_value in request.POST.items():
        if 'section' in field_key:
            if 'boolean' in field_key:
                field_form = field_key.split("boolean_section_id_")[1]
                section = None
                field_model = None
                for field in tool.get_fields():
                    if field.name == field_form:
                        field_model = field
                        section = field.section
                        break

                if section not in tool_data:
                    tool_data[section] = {}
                if field_model.preset_value:
                    value = request.POST.get(
                        f'preset_value_boolean_id_{field_form}', None)
                    if value == "true":
                        value = True
                    if value == "on":
                        value = True
                    if value == "false":
                        value = False
                    tool_data[section][field_model.name] = value
                else:
                    value = request.POST.get(field_form, False)
                    if value == "true":
                        value = True
                    if value == "on":
                        value = True
                    if value == "false":
                        value = False
                    tool_data[section][field_model.name] = value
            else:
                field_form = field_key.split("section_id_")[1]
                section = None
                field_model = None
                for field in tool.get_fields():
                    if field.name == field_form:
                        field_model = field
                        section = field.section
                        break

                if section not in tool_data:
                    tool_data[section] = {}
                if field_model.preset_value:
                    tool_data[section][field_model.name] = request.POST.get(f'preset_value_id_{field_form}', None)
                else:
                    tool_data[section][field_model.name] = request.POST.get(field_form, None)

    return tool_data


def init_exec(tool_data, request):
    machine_found = Machine.objects.get(id=request.session['machine_chosen'])
    user_machine = machine_found.user
    execution = Execution()
    setup = tool_data['setup']
    slurm = tool_data['slurm']
    compss = tool_data['COMPSs']

    execution.machine = machine_found
    execution.author = request.user
    execution.user = user_machine
    execution.status = "INITIALIZING"
    execution.checkpoint = 0
    execution.time = "00:00:00"
    execution.wdir = ""
    execution.setup_path = ""
    execution.execution_time = 0
    execution.jobID = 0
    execution.eID = uuid.uuid4()
    execution.nodes = slurm['Number of Nodes']
    name_sim = setup['Simulation Name']
    execution.name_sim = name_sim
    execution.exec_time = slurm['Time Limit']
    execution.qos = slurm['QOS']
    unique_id = uuid.uuid4()
    execution.name_e = (name_sim + "_" + str(unique_id) + "." + "yaml")
    execution.auto_restart_bool = compss["Autorestart"]
    execution.checkpointBool = compss["Checkpointing"]
    execution.d_bool = compss["Debug"]
    execution.t_bool = compss["Trace"]
    execution.g_bool = compss["Graph"]
    execution.tool = tool_data["tool_name"]
    execution.results_ftp_path = ""
    execution.save()

    return execution.eID


@login_required
def tools(request):
    check_conn_bool = check_connection(request)
    sections = ['application', 'setup', 'slurm', 'COMPSs', 'environment']
    if not check_conn_bool:
        request.session['original_request'] = request.POST
        request.session['redirect_to_tools'] = True
        return redirect('hpc_machines')

    if request.method == 'POST':
        for key in request.POST.keys():
            if 'deleteCustom' in key:
                tool_name = key.split('deleteCustom')[1]
                Tool.objects.get(name=tool_name).delete()
                request.session['tool_deleted'] = tool_name
                return redirect('tools')

            match = re.match(rf'^run(.*)Button$', key)
            if bool(match):
                tool_name = match.group(1)
                tool_data = extract_tool_data(request, tool_name)
                e_id = init_exec(tool_data, request)
                run_sim = RunSimulation(tool_data, request, e_id)
                run_sim.start()
                return redirect('tools')

        if 'stAnalysisButton' in request.POST:
            document_form = DocumentForm(request.POST, request.FILES)
            if document_form.is_valid():
                branch = request.POST.get('branchChoice')
                for filename, file in request.FILES.items():
                    unique_id = uuid.uuid4()
                    name_e = ((str(file).split(".")[0]) + "_" + str(
                        unique_id) + "."
                              + str(file).split(".")[1])
                name = name_e
                document = document_form.save(commit=False)
                document.document.name = name
                document.save()
                num_nodes = request.POST.get('numNodes')
                name_sim = request.POST.get('name_sim')
                qos = request.POST.get('qos')
                exec_time = request.POST.get('execTime')
                checkpoint_flag = request.POST.get("checkpoint_flag")
                auto_restart = request.POST.get("auto_restart")
                g_flag = request.POST.get("gSwitch")
                d_flag = request.POST.get("dSwitch")
                t_flag = request.POST.get("tSwitch")
                auto_restart_bool = False
                checkpoint_bool = False
                if name_sim is None:
                    name_sim = get_random_string(8)
                if checkpoint_flag == "on":
                    checkpoint_bool = True
                if auto_restart == "on":
                    auto_restart_bool = True
                if auto_restart_bool:
                    checkpoint_bool = True
                g_bool = "false"  # graph option
                if g_flag == "on":
                    g_bool = "true"
                t_bool = "false"  # trace option
                if t_flag == "on":
                    t_bool = "true"
                d_bool = "false"  # debug option
                if d_flag == "on":
                    d_bool = "true"
                project_name = request.POST.get('project_name')
                e_id = start_exec(num_nodes, name_sim, exec_time, qos, name,
                                  request,
                                  auto_restart_bool, checkpoint_bool, d_bool,
                                  t_bool, g_bool, branch, tool="stability_analysis")
                run_sim = run_sim_async(request, name, num_nodes, name_sim,
                                        exec_time, qos, checkpoint_bool,
                                        auto_restart_bool, e_id, branch,
                                        g_bool,
                                        t_bool, d_bool, project_name)
                run_sim.start()
                request.session['run_job'] = name_sim
                return redirect('tools')

        if 'resultExecution' in request.POST:
            request.session['jobIDdone'] = request.POST.get(
                "resultExecutionValue")
            return redirect('results')

        elif 'failedExecution' in request.POST:
            request.session['jobIDfailed'] = request.POST.get(
                "failedExecutionValue")
            return redirect('execution_failed')

        elif 'infoExecution' in request.POST:
            request.session['eIDinfo'] = request.POST.get(
                "infoExecutionValue")
            return redirect('info_execution')

        elif 'timeoutExecution' in request.POST:
            request.session['jobIDcheckpoint'] = request.POST.get(
                "timeoutExecutionValue")
            checkpointing_noAutorestart(
                request.POST.get("timeoutExecutionValue"), request)
            return redirect('tools')

        elif 'stopExecution' in request.POST:
            request.session['stopExecutionValue'] = request.POST.get(
                "stopExecutionValue")
            stopExecution(request.POST.get("stopExecutionValue"), request)

        elif 'deleteExecution' in request.POST:
            request.session['deleteExecutionValue'] = request.POST.get(
                "deleteExecutionValue")
            deleteExecution(request.POST.get("deleteExecutionValue"),
                            request)

        machine_connected = Machine.objects.get(
            id=request.session["machine_chosen"])
        executions = Execution.objects.all().filter(author=request.user,
                                                    machine=machine_connected).filter(
            Q(status="PENDING") | Q(status="RUNNING") | Q(
                status="INITIALIZING"))
        executionsDone = Execution.objects.all().filter(
            author=request.user, status="COMPLETED",
            machine=machine_connected)
        executionsFailed = Execution.objects.all().filter(
            author=request.user, status="FAILED",
            machine=machine_connected)
        executionTimeout = Execution.objects.all().filter(
            author=request.user, status="TIMEOUT",
            machine=machine_connected)
        executionsCheckpoint = Execution.objects.all().filter(
            author=request.user, status="TIMEOUT",
            autorestart=True, machine=machine_connected)
        executionsCanceled = Execution.objects.all().filter(
            author=request.user, status="CANCELLED+",
            checkpoint="-1", machine=machine_connected)
        request.session[
            'nameConnectedMachine'] = "" + machine_connected.user + "@" + machine_connected.fqdn
        for execution in executionsCanceled:
            checks = Execution.objects.all().get(author=request.user,
                                                 status="CANCELLED+",
                                                 checkpoint=execution.jobID,
                                                 machine=machine_connected)
            if checks is not None:
                execution.status = "TIMEOUT"
                execution.checkpoint = 0
                execution.save()

        document_form = DocumentForm()
        branches = get_github_repo_branches()

        return render(request, 'pages/tools.html',
                      {'executions': executions,
                       'executionsDone': executionsDone,
                       'executionsFailed': executionsFailed,
                       'executionsTimeout': executionTimeout,
                       'checkConn': "yes",
                       'machine_chosen': request.session[
                           'nameConnectedMachine'],
                       'document_form': document_form,
                       'machines': populate_executions_machines(request),
                       'branches': branches, 'sections': sections,
                       })
    else:
        execution_form = ExecutionForm()

        machine_connected = get_machine_connected(request)
        request.session[
            'nameConnectedMachine'] = f"{machine_connected.user}@{machine_connected.fqdn}"

        executions = Execution.objects.filter(author=request.user,
                                              machine=machine_connected).filter(
            Q(status="PENDING") | Q(status="RUNNING") | Q(
                status="INITIALIZING"))

        executionsDone = Execution.objects.filter(author=request.user,
                                                  machine=machine_connected,
                                                  status="COMPLETED")
        executionsFailed = Execution.objects.filter(author=request.user,
                                                    machine=machine_connected,
                                                    status="FAILED")
        executionTimeout = Execution.objects.filter(author=request.user,
                                                    machine=machine_connected,
                                                    status="TIMEOUT")
        executionsCheckpoint = Execution.objects.filter(author=request.user,
                                                        machine=machine_connected,
                                                        status="TIMEOUT",
                                                        autorestart=True)

        executionsCanceled = Execution.objects.all().filter(
            author=request.user, machine=machine_connected,
            status="CANCELED",
            checkpoint="-1")
        for execution in executionsCanceled:
            checks = Execution.objects.all().get(author=request.user,
                                                 status="CANCELLED+",
                                                 machine=machine_connected,
                                                 checkpoint=execution.jobID)
            if checks is not None:
                execution.status = "TIMEOUT"
                execution.checkpoint = 0
                execution.save()
            checks.delete()
        request.session["checkConn"] = "yes"

        document_form = DocumentForm()
        branches = get_github_repo_branches()
        run_job = request.session.pop('run_job', None)
        tool_created = request.session.pop('tool_created', None)
        tool_edited = request.session.pop('tool_edited', None)
        tool_deleted = request.session.pop('tool_deleted', None)

        tool_forms = {}
        for tool in Tool.objects.all():
            fields = {}
            for f in tool.get_fields():
                fields[f.name.replace("_", " ").lower()] = f
            tool_forms[tool.name] = {
                'form': get_form_from_tool(tool),
                'tool': tool,
                'fields': fields
            }

        return render(request, 'pages/tools.html', {
            'document_form': document_form,
            'machines': populate_executions_machines(request),
            'machine_chosen': request.session['nameConnectedMachine'],
            'branches': branches,
            'executions': executions,
            'executionsDone': executionsDone,
            'executionsFailed': executionsFailed,
            'executionsTimeout': executionTimeout,
            'checkConn': request.session['checkConn'],
            'run_job': run_job,
            'tool_created': tool_created,
            'tool_forms': tool_forms,
            'tool_edited': tool_edited,
            'tool_deleted': tool_deleted,
            'sections': sections,
        })


def get_form_from_tool(tool):
    """
    Generates a Django form class based on the `field_list` of a given Tool instance.

    :param tool: Tool instance
    :return: Django form class
    """
    form_fields = {}

    # Dynamically add fields to the form
    for field in tool.get_fields():
        initial = field.default_value or field.preset_value or None
        disabled = field.preset_value is not None

        if field.type == 'boolean':
            if initial == "true":
                initial = True
            elif initial == "false":
                initial = False
            form_fields[field.name] = forms.BooleanField(
                label=field.name.replace("_", " ").lower(),
                required=False,
                initial=initial,
                disabled=disabled
            )
        else:
            form_fields[field.name] = forms.CharField(
                label=field.name.replace("_", " ").lower(),
                max_length=100,
                required=False,
                widget=forms.TextInput(attrs={'class': 'form-control'}),
                initial=initial,
                disabled=disabled
            )

    # Dynamically create a form class
    DynamicToolForm = type('DynamicToolForm', (forms.Form,), form_fields)

    return DynamicToolForm


@login_required
def hpc_machines(request):
    machines_done = populate_executions_machines(request)
    stability_connection = check_connection(request)
    if request.method == 'POST':
        form = Machine_Form(request.POST)

        if 'connectButton' in request.POST:
            user, fqdn = get_name_fqdn(request.POST.get('machineChoice'))
            machine_found = Machine.objects.get(author=request.user, user=user, fqdn=fqdn)
            try:
                machine_chosen = Key_Gen.objects.filter(machine_id=machine_found.id).get()
            except ObjectDoesNotExist:
                request.session['ssh_keys_not_created'] = True
                return redirect('hpc_machines')

            private_key_encrypted = machine_chosen.private_key
            try:
                private_key_decrypted = decrypt(private_key_encrypted, request.POST.get("token")).decode()
            except Exception:
                request.session['check_existence_machines'] = "yes"
                return render(request, 'pages/hpc_machines.html',
                              {'form': form, 'machines': machines_done,
                                'firstPhase': request.session['firstPhase'],
                               'connected': stability_connection,
                               'check_existence_machines':
                                   request.session['check_existence_machines'],
                               "errorToken": 'yes'})

            request.session["private_key_decrypted"] = private_key_decrypted
            request.session['machine_chosen'] = machine_found.id

            connection, created = Connection.objects.get_or_create(user=request.user)
            connection.machine = machine_found
            connection.status = 'Active'
            connection.save()

            request.session["conn_id"] = connection.conn_id
            threadUpdate = updateExecutions(request, connection.conn_id)
            threadUpdate.start()
            stability_connection = check_connection(request)
            if not stability_connection:
                if not machines_done:
                    request.session['check_existence_machines'] = "no"
                request.session["check_connection_stable"] = "Required"
                return render(request, 'pages/hpc_machines.html',
                              {'machines': machines_done,
                               'check_connection_stable': "no",
                               'connected': stability_connection})


            machine_connected = Machine.objects.get(id=request.session["machine_chosen"])
            str_machine_connected = str(machine_connected.user) + "@" + machine_connected.fqdn
            request.session['nameConnectedMachine'] = "" + machine_connected.user + "@" + machine_connected.fqdn
            if request.session.get('redirect_to_run_sim', False):
                request.session.pop('redirect_to_run_sim')
                if 'original_request' in request.session:
                    request.method = 'POST'
                    request.POST = request.session.pop('original_request')
                    return redirect('run_sim')

            if request.session.get('redirect_to_tools', False):
                request.session.pop('redirect_to_tools')
                if 'original_request' in request.session:
                    request.method = 'POST'
                    request.POST = request.session.pop('original_request')
                    return redirect('tools')

            request.session['firstPhase'] = "yes"
            return render(request, 'pages/hpc_machines.html',
                          {'machines': machines_done, 'form': form,
                           'firstPhase': request.session['firstPhase'],
                           'check_existence_machines': request.session[
                               'check_existence_machines'],
                           'show_connected': str_machine_connected,
                           'connected': stability_connection
                           })

        if 'disconnectButton' in request.POST:
            choice = request.POST.get(
                'machineChoice')
            machine_disconnected = Machine.objects.get(
                id=request.session["machine_chosen"])
            str_machine_disconnected = str(
                machine_disconnected.user) + "@" + machine_disconnected.fqdn

            if choice != str_machine_disconnected:
                return render(request, 'pages/hpc_machines.html',
                              {'machines': machines_done, 'form': form,
                               'firstPhase': request.session['firstPhase'],
                               'check_existence_machines': request.session[
                                   'check_existence_machines'],
                               'show_already_connected': str_machine_disconnected,
                               'connected': stability_connection
                               })
            Connection.objects.filter(user=request.user).update(
                status="Disconnect", machine=None)

            for key in list(request.session.keys()):
                if not key.startswith("_"):
                    del request.session[key]
            request.session['firstPhase'] = "yes"
            request.session['check_existence_machines'] = "yes"

            stability_connection = check_connection(request)
            if not stability_connection:
                if not machines_done:
                    request.session['check_existence_machines'] = "no"
                request.session["check_connection_stable"] = "Required"
            return render(request, 'pages/hpc_machines.html',
                          {'machines': machines_done, 'form': form,
                           'firstPhase': request.session['firstPhase'],
                           'check_existence_machines': request.session[
                               'check_existence_machines'],
                           'connected': stability_connection,
                           'show_disconnected': str_machine_disconnected
                           })

        if 'detailsButton' in request.POST:
            request.session['firstPhase'] = "no"
            machine = request.POST.get('machineChoice')
            user = machine.split("@")[0]
            fqdn = machine.split("@")[1]
            machine_found = Machine.objects.get(author=request.user,
                                                user=user, fqdn=fqdn)
            machineID = machine_found.id
            request.session['machineID'] = machineID
            form = Machine_Form(
                initial={'fqdn': machine_found.fqdn,
                         'user': machine_found.user,
                         'wdir': machine_found.wdir,
                         'installDir': machine_found.installDir,
                         'dataDir': machine_found.dataDir,
                         'id': machine_found.id,
                         'author': machine_found.author})
            return render(request, 'pages/hpc_machines.html',
                          {'form': form, 'machines': machines_done,
                           'firstPhase': request.session['firstPhase'],
                           'connected': stability_connection,
                           'check_existence_machines':
                               request.session['check_existence_machines']})

        if 'deleteButton' in request.POST:
            request.session['firstPhase'] = "yes"
            machine = request.POST.get('machineChoice')
            user = machine.split("@")[0]
            fqdn = machine.split("@")[1]
            machine_found = Machine.objects.get(author=request.user,
                                                user=user, fqdn=fqdn)
            machine_found.delete()
            machines_done = populate_executions_machines(request)
            return render(request, 'pages/hpc_machines.html',
                          {'form': form, 'machines': machines_done,
                           'firstPhase': request.session['firstPhase'],
                           'connected': stability_connection,
                           'machine_deleted': machine,
                           'check_existence_machines':
                               request.session['check_existence_machines']})

        if 'redefineButton' in request.POST:
            if (form.is_valid()):
                machine_found = Machine.objects.get(
                    id=request.session['machineID'])
                userForm = form['user'].value()
                fqdnForm = form['fqdn'].value()
                wdirForm = form['wdir'].value()
                installDirForM = form['installDir'].value()
                dataDirForM = form['dataDir'].value()
                Machine.objects.filter(
                    id=request.session['machineID']).update(user=userForm,
                                                            wdir=wdirForm,
                                                            fqdn=fqdnForm,
                                                            installDir=installDirForM,
                                                            dataDir=dataDirForM)
                request.session['firstPhase'] = "yes"
                request.session['machine_created'] = f"{userForm}@{fqdnForm}"
                return redirect('hpc_machines')
    else:
        form = Machine_Form()
        request.session["check_connection_stable"] = "no"
        request.session["check_existence_machines"] = "yes"
        show_warning = (request.session.get('redirect_to_run_sim', False) or
                         request.session.get('redirect_to_tools', False))

        if not machines_done:
            request.session['check_existence_machines'] = "no"
            request.session['firstPhase'] = "no"

        else:
            request.session['firstPhase'] = "yes"

        machine_created = request.session.get('machine_created', None)
        request.session.pop('machine_created', None)

        ssh_keys_error = request.session.get('ssh_keys_not_created', None)
        request.session.pop('ssh_keys_not_created', None)

        tool_created = request.session.get('tool_created', None)
        request.session.pop('tool_created', None)

        return render(request, 'pages/hpc_machines.html',
                      {'machines': machines_done, 'form': form,
                       'firstPhase': request.session['firstPhase'],
                       'check_existence_machines': request.session['check_existence_machines'],
                       'show_warning': show_warning, 'connected': stability_connection,
                       'machine_created': machine_created, 'ssh_keys_error': ssh_keys_error,
                       'tool_created': tool_created})


def get_name_fqdn(machine):
    user = machine.split("@")[0]
    fqdn = machine.split("@")[1]
    return user, fqdn


def check_connection(request):
    connection = Connection.objects.filter(user=request.user, status="Active")
    if len(connection) > 0:
        return True
    return False


def connection_ssh(private_key_decrypted, machineID):
    try:
        ssh = paramiko.SSHClient()
        pkey = paramiko.RSAKey.from_private_key(StringIO(private_key_decrypted))
        machine_found = Machine.objects.get(id=machineID)
        ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        ssh.connect(machine_found.fqdn, username=machine_found.user, pkey=pkey)
        return ssh
    except paramiko.AuthenticationException as auth_error:
        print(f"Authentication error: {auth_error}")
    except paramiko.BadHostKeyException as host_key_error:
        print(f"Bad host key error: {host_key_error}")
    except paramiko.SSHException as ssh_error:
        print(f"SSH error: {ssh_error}")
    except Machine.DoesNotExist as not_found_error:
        print(f"Machine not found error: {not_found_error}")
    except Exception as e:
        print(f"An unexpected error occurred: {e}")
        return redirect("tools")


"""def is_recaptcha_valid(request):
    try:
        response = requests.post(
            settings.GOOGLE_VERIFY_RECAPTCHA_URL,
            data={
                'secret': settings.RECAPTCHA_SECRET_KEY,
                'response': request.POST.get('g-recaptcha-response'),
                'remoteip': get_client_ip(request)
            },
            verify=True
        )
        response_json = response.json()
        return response_json.get("success", False)"""


################### RUN SIMULATIONS ####################

# Generate a random string of specified length
def get_random_string(length):
    # With combination of lower and upper case
    result_str = ''.join(random.choice(string.ascii_letters) for i in range(length))
    return result_str


def run_command(command):
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, shell=True)
    if result.returncode != 0:
        print(f"Error executing command: {command}")
        print(result.stderr)
        return None
    return result.stdout.strip()


def get_github_repo_branches():
    try:
        with open(os.path.expanduser('~/keys/github-token-hp2cdt.txt'),
                  'r') as file:
            token = file.read().strip()
    except FileNotFoundError:
        return "Unable to read the token from ~/keys/github-token-hp2cdt.txt"
    except PermissionError:
        return "Unable to access the file ~/keys/github-token-hp2cdt.txt: PermissionError"

    user_repo = "MauroGarciaLorenzo/hp2c-dt"
    api_url = f"https://api.github.com/repos/{user_repo}/branches"

    headers = {
        'Authorization': f'Token {token}'
    }

    response = requests.get(api_url, headers=headers)

    if response.status_code == 200:
        branches = response.json()
        return [branch['name'] for branch in branches]
    else:
        return f"Unable to access the GitHub repository. Status code: {response.status_code}"


def start_exec(num_nodes, name_sim, execTime, qos, name, request, auto_restart_bool, checkpoint_bool, d_bool, t_bool,
               g_bool, branch, tool=""):
    machine_found = Machine.objects.get(id=request.session['machine_chosen'])
    user_machine = machine_found.user
    principal_folder = machine_found.wdir
    uID = uuid.uuid4()
    form = Execution()
    form.eID = uID
    form.jobID = 0
    form.user = user_machine
    form.author = request.user
    form.nodes = num_nodes
    form.status = "INITIALIZING"
    form.checkpoint = 0
    form.checkpointBool = checkpoint_bool
    form.time = "00:00:00"
    form.wdir = ""
    form.setup_path = ""
    form.execution_time = 0
    form.qos = qos
    form.name_sim = name_sim
    form.autorestart = auto_restart_bool
    form.machine = machine_found
    form.d_bool = d_bool
    form.t_bool = t_bool
    form.g_bool = g_bool
    form.branch = branch
    form.results_ftp_path = ""
    form.tool = tool
    form.save()
    return uID


def checkpointing_noAutorestart(jobIDCheckpoint, request):
    ssh = connection_ssh(request.session['private_key_decrypted'], request.session['machine_chosen'])
    checkpointID = Execution.objects.all().get(author=request.user, jobID=jobIDCheckpoint)
    machine_connected = Machine.objects.get(id=request.session['machine_chosen'])
    command = "source /etc/profile; cd " + checkpointID.wdir + "; source checkpoint_script.sh;"
    stdin, stdout, stderr = ssh.exec_command(command)
    stdout = stdout.readlines()
    s = "Submitted batch job"
    while (len(stdout) == 0):
        import time
        time.sleep(1)
    if (len(stdout) > 1):
        for line in stdout:
            if (s in line):
                jobID = int(line.replace(s, ""))
                request.session['jobID'] = jobID
                form = Execution()
                form.jobID = jobID
                form.eID = uuid.uuid4()
                form.machine_id = checkpointID.machine_id
                form.user = checkpointID.user
                form.author = request.user
                form.nodes = checkpointID.nodes
                form.status = "PENDING"
                form.checkpoint = checkpointID.jobID
                form.time = "00:00:00"
                form.wdir = checkpointID.wdir
                form.workflow_path = checkpointID.workflow_path
                form.execution_time = int(checkpointID.execution_time)
                time = int(checkpointID.execution_time)
                form.name_workflow = checkpointID.name_workflow
                form.qos = checkpointID.qos
                form.name_sim = checkpointID.name_sim
                form.autorestart = checkpointID.autorestart
                form.checkpointBool = checkpointID.checkpointBool
                form.d_bool = checkpointID.d_bool
                form.t_bool = checkpointID.t_bool
                form.g_bool = checkpointID.g_bool
                form.branch = checkpointID.branch
                form.save()
    checkpointID = Execution.objects.all().get(author=request.user, jobID=jobIDCheckpoint)
    checkpointID.status = "CONTINUE"
    checkpointID.save()
    return


def get_id_from_string(machine, author):
    user, fqdn = get_name_fqdn(machine)
    machine_found = Machine.objects.get(author=author, user=user, fqdn=fqdn)
    return machine_found.id


def stopExecution(eIDstop, request):
    ssh = connection_ssh(request.session['private_key_decrypted'], request.session['machine_chosen'])
    exec = Execution.objects.filter(eID=eIDstop).get()
    if exec.eID != 0:
        command = "scancel " + str(exec.jobID)
        stdin, stdout, stderr = ssh.exec_command(command)
    Execution.objects.filter(eID=eIDstop).update(status="CANCELLED+")
    form = ExecutionForm()
    executions = Execution.objects.all().filter(author=request.user).filter(
        Q(status="PENDING") | Q(status="RUNNING") | Q(status="INITIALIZING"))
    executionsDone = Execution.objects.all().filter(author=request.user, status="COMPLETED")
    executionsFailed = Execution.objects.all().filter(author=request.user, status="FAILED")
    executionTimeout = Execution.objects.all().filter(author=request.user, status="TIMEOUT", autorestart=False,
                                                      checkpointBool=True)
    return render(request, 'pages/executions.html',
                  {'form': form, 'executions': executions, 'executionsDone': executionsDone,
                   'executionsFailed': executionsFailed, 'executionsTimeout': executionTimeout})


def delete_parent_folder(path, ssh):
    parent_folder = os.path.dirname(path)
    command = "rm -rf " + parent_folder + "/"
    stdin, stdout, stderr = ssh.exec_command(command)
    return


def deleteExecution(eIDdelete, request):
    try:
        ssh = connection_ssh(request.session['private_key_decrypted'], request.session['machine_chosen'])
        exec = Execution.objects.filter(eID=eIDdelete).get()
        delete_parent_folder(exec.wdir, ssh)
        if exec.eID != 0:
            command = "scancel " + str(exec.jobID)
            stdin, stdout, stderr = ssh.exec_command(command)
        Execution.objects.filter(eID=eIDdelete).delete()
        form = ExecutionForm()
        executions = Execution.objects.all().filter(author=request.user).filter(
            Q(status="PENDING") | Q(status="RUNNING") | Q(status="INITIALIZING"))
        executionsDone = Execution.objects.all().filter(author=request.user, status="COMPLETED")
        executionsFailed = Execution.objects.all().filter(author=request.user, status="FAILED")
        executionTimeout = Execution.objects.all().filter(author=request.user, status="TIMEOUT", autorestart=False,
                                                          checkpointBool=True)
        return render(request, 'pages/executions.html',
                      {'form': form, 'executions': executions, 'executionsDone': executionsDone,
                       'executionsFailed': executionsFailed, 'executionsTimeout': executionTimeout})
    except:
        Execution.objects.filter(eID=eIDdelete).delete()
        form = ExecutionForm()
        executions = Execution.objects.all().filter(author=request.user).filter(
            Q(status="PENDING") | Q(status="RUNNING") | Q(status="INITIALIZING"))
        executionsDone = Execution.objects.all().filter(author=request.user, status="COMPLETED")
        executionsFailed = Execution.objects.all().filter(author=request.user, status="FAILED")
        executionTimeout = Execution.objects.all().filter(author=request.user, status="TIMEOUT", autorestart=False,
                                                          checkpointBool=True)
        return render(request, 'pages/executions.html',
                      {'form': form, 'executions': executions, 'executionsDone': executionsDone,
                       'executionsFailed': executionsFailed, 'executionsTimeout': executionTimeout})


def update_table(request):
    machine_found = Machine.objects.get(id=request.session['machine_chosen'])
    machineID = machine_found.id
    date_format = "%Y-%m-%dT%H:%M:%S"
    ssh = connection_ssh(request.session["private_key_decrypted"], machineID)
    executions = Execution.objects.all().filter(author=request.user, machine=request.session['machine_chosen']).filter(
        Q(status="PENDING") | Q(status="RUNNING") | Q(status="INITIALIZING"))
    for executionE in executions:
        if executionE.jobID != 0:
            stdin, stdout, stderr = ssh.exec_command(
                "sacct -j " + str(executionE.jobID) + " --format=jobId,user,nnodes,elapsed,state,submit,start,end | sed -n 3,3p")
            stdout = stdout.readlines()
            values = str(stdout).split()
            Execution.objects.filter(jobID=executionE.jobID).update(time=values[3])

            submit_date = values[5]
            if submit_date != "Unknown":
                submit_date = datetime.strptime(submit_date, date_format)
                Execution.objects.filter(jobID=executionE.jobID).update(
                    submit=submit_date)

            start_date = values[6]
            if start_date != "Unknown":
                start_date = datetime.strptime(start_date, date_format)
                Execution.objects.filter(jobID=executionE.jobID).update(
                    start=start_date)

            end_date = values[7]
            if end_date != "Unknown":
                end_date = datetime.strptime(end_date, date_format)
                Execution.objects.filter(jobID=executionE.jobID).update(
                    end=end_date)

            if str(values[4]) != executionE.status:
                Execution.objects.filter(jobID=executionE.jobID).update(
                    status=values[4], time=values[3],
                    nodes=int(values[2]))
                
    return True


def get_last_subdirectory(url):
    # Split the URL by '/' and get the last element
    return url.rstrip('/').split('/')[-1]


def remove_protocol_and_domain(url):
    # Remove protocol and domain
    return re.sub(r'^.*?//[^/]+/', '', url)


@login_required
def results(request):
    if request.method == 'POST':
        pass
    else:
        jobID = request.session['jobIDdone']
        ssh = connection_ssh(request.session['private_key_decrypted'], request.session['machine_chosen'])
        stdin, stdout, stderr = ssh.exec_command(
            "sacct -j " + str(jobID) + " --format=jobId,user,nnodes,elapsed,state | sed -n 3,3p")
        stdout = stdout.readlines()
        values = str(stdout).split()
        Execution.objects.filter(jobID=jobID).update(status=values[4], time=values[3], nodes=int(values[2]))
        execUpdate = Execution.objects.get(jobID=jobID)

        files, remote_path = get_files(execUpdate.wdir, execUpdate.results_dir,
                                       request.session['private_key_decrypted'],
                                       request.session['machine_chosen'])
        files = dict(sorted(files.items()))

        request.session['remote_path'] = remote_path
        request.session['results_dir'] = execUpdate.results_dir
    return render(request, 'pages/results.html',
                  {'executionsDone': execUpdate, 'files': files})


def copy_folder_hpc_to_service(request, service_local_path, remote_hpc_path):
    ssh = paramiko.SSHClient()
    pkey = paramiko.RSAKey.from_private_key(StringIO(request.session["private_key_decrypted"]))
    machine_found = Machine.objects.get(id=request.session['machine_chosen'])  # Assuming this is your custom code
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(machine_found.fqdn, username=machine_found.user, pkey=pkey)
    sftp = ssh.open_sftp()
    download_directory(sftp, remote_hpc_path, service_local_path)
    sftp.close()
    ssh.close()
    return


def download_directory(sftp, remote_dir, local_dir, depth=0, max_depth=10):
    if depth > max_depth:
        print("Maximum recursion depth reached.")
        return
    os.makedirs(local_dir, exist_ok=True)

    try:
        items = sftp.listdir_attr(remote_dir)
    except Exception as e:
        print(f"Error listing directory {remote_dir}: {e}")
        return

    for item in items:
        remote_item = f"{remote_dir}/{item.filename}"
        local_item = os.path.join(local_dir, item.filename)

        if S_ISDIR(item.st_mode):
            download_directory(sftp, remote_item, local_item, depth=depth + 1, max_depth=max_depth)
        else:
            try:
                sftp.get(remote_item, local_item)
            except Exception as e:
                print(f"Error downloading {remote_item}: {e}")
    return


@login_required
def download_file(request, file_name):
    remote_path = request.session['remote_path']
    private_key_decrypted = request.session['private_key_decrypted']
    machineID = request.session['machine_chosen']

    ssh = paramiko.SSHClient()
    pkey = paramiko.RSAKey.from_private_key(StringIO(private_key_decrypted))
    machine_found = Machine.objects.get(id=machineID)
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(machine_found.fqdn, username=machine_found.user, pkey=pkey)
    sftp = ssh.open_sftp()
    try:
        file_path = find_file_recursively(sftp, remote_path, file_name)
        if file_path:
            with sftp.file(file_path, 'rb') as file_obj:
                file_data = file_obj.read()
                response = HttpResponse(file_data, content_type='application/octet-stream')
                response['Content-Disposition'] = f'attachment; filename="{os.path.basename(file_path)}"'
                return response
        else:
            raise Http404("File not found.")
    except IOError:
        raise Http404("Error accessing the file.")
    finally:
        sftp.close()
        ssh.close()


def find_file_recursively(sftp, remote_path, file_name):
    for entry in sftp.listdir_attr(remote_path):
        entry_path = os.path.join(remote_path, entry.filename)
        if S_ISDIR(entry.st_mode):
            found_path = find_file_recursively(sftp, entry_path, file_name)
            if found_path:
                return found_path
        elif entry.filename == file_name:
            return entry_path
    return None


def render_right(request):
    form = Machine_Form()
    checkConnBool = check_connection(request)
    machines_done = populate_executions_machines(request)
    if not checkConnBool:
        if not machines_done:
            request.session['firstCheck'] = "no"
        request.session["checkConn"] = "Required"
    return render(request, 'pages/hpc_machines.html',
                  {'form': form, 'machines': machines_done,
                   'firstPhase': request.session['firstPhase'],
                   'connected': checkConnBool,
                   'check_existence_machines':
                       request.session['check_existence_machines']})


def execute_command(ssh, cmd):
    print()
    print("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&")
    print("EXECUTING COMMAND:")
    print(cmd)
    stdin, stdout, stderr = ssh.exec_command("source /etc/profile; " + cmd)
    print("-------------START STDOUT--------------")
    print("".join(stdout))
    print("---------------END STDOUT--------------")
    print("-------------START STDERR--------------")
    print("".join(stderr))
    print("---------------END STDERR--------------")
    print("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&")
    print()


class updateExecutions(threading.Thread):
    def __init__(self, request, connectionID):
        threading.Thread.__init__(self)
        self.request = request
        self.timeout = 120 * 60
        self.connectionID = connectionID

    def run(self):
        timeout_start = time.time()
        while time.time() < timeout_start + self.timeout:
            if not check_connection(self.request):
                break

            bool_exception = False
            try:
                bool_exception = update_table(self.request)
            except Exception as e:
                print("Error updating the table of executions: ", e)

            if not bool_exception:
                break
            time.sleep(5)

        Connection.objects.filter(user=self.request.user).update(status="Disconnect", machine=None)
        render_right(self.request)
        return

class RunSimulation(threading.Thread):
    def __init__(self, tool_data, request, e_id):
        threading.Thread.__init__(self)
        self.e_id = e_id
        self.request = request
        self.tool_data = tool_data

        for key in tool_data.keys():
            if isinstance(tool_data.get(key), str):
                try:
                    self.tool_data[key] = json.loads(tool_data[key])
                except json.JSONDecodeError:
                    pass

        self.application = self.tool_data["application"]
        self.setup = self.tool_data["setup"]
        self.slurm = self.tool_data["slurm"]
        self.compss = self.tool_data["COMPSs"]
        self.environment = self.tool_data["environment"]


    def run(self):
        execution = Execution.objects.get(eID=self.e_id)
        machine_found = Machine.objects.get(
            id=self.request.session['machine_chosen'])
        fqdn = machine_found.fqdn
        machine_folder = extract_substring(fqdn)
        userMachine = machine_found.user
        principal_folder = self.setup["Working Dir"]
        wdirPath, nameWdir = wdir_folder(principal_folder)
        setup_file = execution.name_sim.replace(" ", "_") + ".yaml"
        setup_path = f"{principal_folder}/{nameWdir}/setup/{setup_file}"

        tool_data_yaml = yaml.dump(self.tool_data)

        ssh = connection_ssh(self.request.session["private_key_decrypted"],
                             machine_found.id)

        cmd1 = Script(ssh)
        cmd1.append(f"mkdir -p {principal_folder}/{nameWdir}/setup/")
        cmd1.append(f"echo {shlex.quote(tool_data_yaml)} > {setup_path}")
        cmd1.append(f"cd {principal_folder}")
        cmd1.append("BACKUPDIR=$(ls -td ./*/ | head -1)")
        cmd1.append(f"echo EXECUTION_FOLDER:$BACKUPDIR")
        cmd1.execute()

        execution_folder = wdirPath + "/execution"
        setup_folder = wdirPath + "/setup"
        local_path = os.path.join(os.getenv("HOME"), "ui-hp2cdt",
                                  self.tool_data["tool_name"])

        Execution.objects.filter(eID=self.e_id).update(wdir=execution_folder, setup_path=setup_folder)

        github_setup = json.loads(self.setup["github"])

        for repo in github_setup:
            repo_name = repo["url"].split("/")[4]
            remote_path = os.path.join(self.setup["Install Dir"], repo_name)
            sftp_upload_repository(local_path=local_path,
                                   remote_path=remote_path,
                                   private_key_decrypted=self.request.session[
                                       "private_key_decrypted"],
                                   machine_id=machine_found.id,
                                   branch=repo["branch"],
                                   url=repo["url"], install=repo["install"],
                                   install_dir=repo["install_dir"],
                                   editable=repo["editable"],
                                   requirements=repo["requirements"],
                                   target=repo["target"],
                                   modules=self.slurm["modules"])
            print()
            print("UPLOADED REPO", repo["url"])
            print()


def sftp_upload_repository(local_path, remote_path, private_key_decrypted,
                           machine_id, branch, url, install, install_dir,
                           editable, requirements, target, modules, retry=False):
    repo_name = remote_path.split("/")[-1]
    res = get_github_code(repo_name, url, branch, local_path)
    ssh = paramiko.SSHClient()
    pkey = paramiko.RSAKey.from_private_key(StringIO(private_key_decrypted))
    machine_found = Machine.objects.get(id=machine_id)
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(machine_found.fqdn, username=machine_found.user, pkey=pkey)
    sftp = ssh.open_sftp()

    # Check and create remote folder if it doesn't exist
    if not remote_path.startswith('/'):
        stdin, stdout, stderr = ssh.exec_command("echo $HOME")
        stdout = "".join(stdout.readlines()).strip()
        remote_path = stdout + "/" + remote_path

    remote_dirs = remote_path.split('/')
    current_dir = ''
    emptyDir = False
    for dir in remote_dirs:
        if dir:
            current_dir += '/' + dir
            try:
                sftp.stat(current_dir)
            except FileNotFoundError:
                print("Creating directory " + str(current_dir))
                sftp.mkdir(current_dir)
                emptyDir = True

    if res or emptyDir or retry:
        # Recursively upload the local folder and its contents
        for root, dirs, files in os.walk(
                local_path + f"/{repo_name}/" + branch):
            if '.git' in dirs:
                dirs.remove('.git')

            if '.idea' in dirs:
                dirs.remove('.idea')

            # Calculate the relative path from local_path to root
            relative_root = os.path.relpath(root,
                                            local_path + f"/{repo_name}/" + branch)

            # Determine the remote directory
            if relative_root == '.':
                remote_dir = remote_path
            else:
                remote_dir = os.path.join(remote_path, relative_root)

            # Ensure the remote directory exists
            try:
                sftp.stat(remote_dir)
            except FileNotFoundError:
                print(f"{remote_dir} does not exist. Creating it.")
                sftp.mkdir(remote_dir)

            for file in files:
                local_file = os.path.join(root, file)
                remote_file = os.path.join(remote_dir, file)
                try:
                    sftp.put(local_file, remote_file)
                except Exception as e:
                    print(
                        f"Failed to upload {local_file} to {remote_file}: {e}")

    script = Script(ssh)
    for module in modules:
        script.append(f"module load {module}")

    if install:
        installation_dir = os.path.join(remote_path, install_dir) # install_dir can be empty
        editable_option = ""
        if editable:
            editable_option = "-e"

        script.append(f"pip install {editable_option} {installation_dir}")

    if requirements:
        target_option = ""
        if target:
            target_option = f"--target={remote_path}/packages"

        script.append(
            f"pip install -r {remote_path}/requirements.txt {target_option}")

    script.execute()
    sftp.close()
    return


class Script():
    def __init__(self, ssh):
        self.script = ""
        self.ssh = ssh

    def append(self, cmd):
        self.script += f"{cmd}; "

    def execute(self):
        self.script = "source /etc/profile; " + self.script
        print()
        print("EXECUTING COMMAND:")
        print(self.script)
        stdin, stdout, stderr = self.ssh.exec_command(self.script)
        stdout = stdout.readlines()
        stderr = stderr.readlines()
        print("-------------START STDOUT--------------")
        print("".join(stdout))
        print("---------------END STDOUT--------------")
        print("-------------START STDERR--------------")
        print("".join(stderr))
        print("---------------END STDERR--------------")


class run_sim_async(threading.Thread):
    def __init__(self, request, name, num_nodes, name_sim, execTime, qos,
                 checkpoint_bool, auto_restart_bool,eID,
                 branch, gOPTION, tOPTION, dOPTION, project_name):
        threading.Thread.__init__(self)
        self.request = request
        self.name = name
        self.numNodes = num_nodes
        self.name_sim = name_sim
        self.execTime = execTime
        self.qos = qos
        self.checkpoint_bool = checkpoint_bool
        self.auto_restart_bool = auto_restart_bool
        self.eiD = eID
        self.branch = branch
        self.gOPTION = gOPTION
        self.tOPTION = tOPTION
        self.dOPTION = dOPTION
        self.project_name = project_name

    def run(self):
        setup = read_and_write_yaml(self.name)
        local_path = os.path.join(os.getenv("HOME"), "ui-hp2cdt")
        machine_found = Machine.objects.get(id=self.request.session['machine_chosen'])
        fqdn = machine_found.fqdn
        machine_folder = extract_substring(fqdn)
        userMachine = machine_found.user
        principal_folder = machine_found.wdir # folder specified as workind dir in the online service
        wdirPath, nameWdir = wdir_folder(principal_folder)
        # command to create all the folders and copy the yaml file passed through the service
        setup_path = f"{principal_folder}/{nameWdir}/setup/{str(self.name)}"
        cmd1 = (
            f"mkdir -p {principal_folder}/{nameWdir}/setup/; "
            f"echo {shlex.quote(str(setup))} > {setup_path};"
            f"cd {principal_folder}; BACKUPDIR=$(ls -td ./*/ | head -1); echo EXECUTION_FOLDER:$BACKUPDIR;")
        print(f"cmd1 : {cmd1}")

        ssh = connection_ssh(self.request.session["private_key_decrypted"], machine_found.id)

        execute_command(ssh, cmd1)

        execution_folder = wdirPath + "/execution"
        setup_folder = wdirPath + "/setup"
        results_dir = os.path.join(local_path, "results", nameWdir)

        Execution.objects.filter(eID=self.eiD).update(wdir=execution_folder,
                                                      setup_path=setup_folder,
                                                      results_dir=results_dir)

        self.request.session['workflow_path'] = setup_folder

        stability_analysis_branch = "new-GridCal"
        gridcal_branch = "205_ACOPF"

        path_install_dir = os.path.join(machine_found.installDir,
                                                "datagen", self.branch)
        path_install_dir_stability_analysis = os.path.join(
            machine_found.installDir, "stability_analysis", stability_analysis_branch)
        path_install_dir_gridcal = os.path.join(machine_found.installDir,
                                                "new-GridCal", gridcal_branch)
        machine_name = remove_numbers(machine_found.fqdn)
        machine_node = machine_found.fqdn.split(".")[0]

        local_folder = os.path.join(local_path, "installDir")
        self.upload_repositories(gridcal_branch, local_folder, machine_found,
                                 path_install_dir, path_install_dir_gridcal,
                                 path_install_dir_stability_analysis,
                                 stability_analysis_branch,
                                 repositories=["datagen", "stability_analysis",
                                               "new-GridCal"])

        stderr, stdout = self.execute_cmds(execution_folder, machine_found,
                                           machine_name, machine_node,
                                           path_install_dir,
                                           path_install_dir_gridcal,
                                           path_install_dir_stability_analysis,
                                           setup, setup_folder, ssh,
                                           userMachine)

        retry_repositories = set()
        for s in stderr:
            if ("Error submitting script to queue system" or
                    "Batch job submission failed" in s):
                Execution.objects.filter(eID=self.eiD).update(status="FAILED")
            if "No such file or directory" in s:
                if machine_found.installDir in s:
                    rest_of_the_string = s.split(machine_found.installDir)[1]
                    repository = rest_of_the_string.split("/")[1]
                    retry_repositories.add(repository)

        if len(retry_repositories) > 0:
            self.upload_repositories(gridcal_branch, local_folder,
                                     machine_found,
                                     path_install_dir,
                                     path_install_dir_gridcal,
                                     path_install_dir_stability_analysis,
                                     stability_analysis_branch,
                                     repositories=retry_repositories,
                                     retry=True)

            stderr, stdout = self.execute_cmds(execution_folder,
                                               machine_found,
                                               machine_name, machine_node,
                                               path_install_dir,
                                               path_install_dir_gridcal,
                                               path_install_dir_stability_analysis,
                                               setup, setup_folder, ssh,
                                               userMachine)

        s = "Submitted batch job"
        var = ""
        while (len(stdout) == 0):
            time.sleep(1)
        if (len(stdout) > 1):
            for line in stdout:
                if (s in line):
                    jobID = int(line.replace(s, ""))
                    Execution.objects.filter(eID=self.eiD).update(jobID=jobID, status="PENDING")
                    self.request.session['jobID'] = jobID
        self.request.session['execution_folder'] = execution_folder
        os.remove("documents/" + str(self.name))
        return

    def execute_cmds(self, execution_folder, machine_found, machine_name,
                     machine_node, path_install_dir, path_install_dir_gridcal,
                     path_install_dir_stability_analysis, setup, setup_folder,
                     ssh, userMachine):
        exported_variables = set_environment_variables(setup)
        if self.checkpoint_bool:
            cmd2 = (
                f"source /etc/profile; source {path_install_dir}/scripts/load.sh "
                f"{path_install_dir} {path_install_dir_stability_analysis} {path_install_dir_gridcal} {machine_name} {machine_node}; "
                f"{get_variables_exported(exported_variables)} mkdir -p {execution_folder}; "
                f"cd {path_install_dir}/scripts/{machine_name}/; source app-checkpoint.sh {userMachine} {str(self.name)} {setup_folder} "
                f"{execution_folder} {self.numNodes} {self.execTime} {self.qos} {machine_found.installDir} {self.branch} {machine_found.dataDir} "
                f"{self.gOPTION} {self.tOPTION} {self.dOPTION} {self.project_name} {self.name_sim};")
            cmd_writeFile_checkpoint = (
                f"source /etc/profile; source {path_install_dir}/scripts/load.sh "
                f"{path_install_dir} {path_install_dir_stability_analysis} {path_install_dir_gridcal} {machine_name} {machine_node}; "
                f"{get_variables_exported(exported_variables)} cd {path_install_dir}/scripts/{machine_name}/; "
                f"source app-checkpoint.sh {userMachine} {str(self.name)} {setup_folder} {execution_folder} {self.numNodes} "
                f"{self.execTime} {self.qos} {machine_found.installDir} {self.branch} {machine_found.dataDir} {self.gOPTION} "
                f"{self.tOPTION} {self.dOPTION} {self.project_name} {self.name_sim};")
            cmd2 += write_checkpoint_file(execution_folder,
                                          cmd_writeFile_checkpoint)
        else:
            cmd2 = (
                f"source /etc/profile; source {path_install_dir}/scripts/load.sh "
                f"{path_install_dir} {path_install_dir_stability_analysis} {path_install_dir_gridcal} {machine_name} {machine_node}; "
                f"{get_variables_exported(exported_variables)} mkdir -p {execution_folder}; "
                f"cd {path_install_dir}/scripts/{machine_name}/; source app.sh {userMachine} {str(self.name)} {setup_folder} "
                f"{execution_folder} {self.numNodes} {self.execTime} {self.qos} {path_install_dir} {self.branch} {machine_found.dataDir} "
                f"{self.gOPTION} {self.tOPTION} {self.dOPTION} {self.project_name} {self.name_sim};")
        print(f"run_sim : {cmd2} ")
        stdin, stdout, stderr = ssh.exec_command(cmd2)
        stdout = stdout.readlines()
        stderr = stderr.readlines()
        print("-------------START STDOUT--------------")
        print("".join(stdout))
        print("---------------END STDOUT--------------")
        print("-------------START STDERR--------------")
        print("".join(stderr))
        print("---------------END STDERR--------------")
        return stderr, stdout

    def upload_repositories(self, gridcal_branch, local_folder, machine_found,
                            path_install_dir, path_install_dir_gridcal,
                            path_install_dir_stability_analysis,
                            stability_analysis_branch, repositories,
                            retry=False):
        for repo in repositories:
            install_dir = path_install_dir
            branch = self.branch
            if repo == "stability_analysis":
                branch = stability_analysis_branch
                install_dir = path_install_dir_stability_analysis
            if repo == "new-GridCal":
                branch = gridcal_branch
                install_dir = path_install_dir_gridcal
            scp_upload_code_folder(local_folder, install_dir,
                                   self.request.session["private_key_decrypted"],
                                   machine_found.id, branch=branch,
                                   repository=repo, local_folder=local_folder,
                                   retry=retry)


def scp_upload_code_folder(local_path, remote_path, private_key_decrypted, machineID, branch,
                           repository, local_folder, retry):
    res = get_github_code(repository, branch, local_folder)
    ssh = paramiko.SSHClient()
    pkey = paramiko.RSAKey.from_private_key(StringIO(private_key_decrypted))
    machine_found = Machine.objects.get(id=machineID)
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(machine_found.fqdn, username=machine_found.user, pkey=pkey)
    sftp = ssh.open_sftp()

    # Check and create remote folder if it doesn't exist
    if not remote_path.startswith('/'):
        stdin, stdout, stderr = ssh.exec_command("echo $HOME")
        stdout = "".join(stdout.readlines()).strip()
        remote_path = stdout + "/" + remote_path

    remote_dirs = remote_path.split('/')
    current_dir = ''
    emptyDir = False
    for dir in remote_dirs:
        if dir:
            current_dir += '/' + dir
            try:
                sftp.stat(current_dir)
            except FileNotFoundError:
                print("Creating directory " + str(current_dir))
                sftp.mkdir(current_dir)
                emptyDir = True

    if res or emptyDir or retry:
        # Recursively upload the local folder and its contents
        for root, dirs, files in os.walk(local_path + f"/{repository}/" + branch):
            if '.git' in dirs:
                dirs.remove('.git')

            if '.idea' in dirs:
                dirs.remove('.idea')

            # Calculate the relative path from local_path to root
            relative_root = os.path.relpath(root, local_path + f"/{repository}/" + branch)

            # Determine the remote directory
            if relative_root == '.':
                remote_dir = remote_path
            else:
                remote_dir = os.path.join(remote_path, relative_root)

            # Ensure the remote directory exists
            try:
                sftp.stat(remote_dir)
            except FileNotFoundError:
                print(f"{remote_dir} does not exist. Creating it.")
                sftp.mkdir(remote_dir)

            for file in files:
                local_file = os.path.join(root, file)
                remote_file = os.path.join(remote_dir, file)
                try:
                    sftp.put(local_file, remote_file)
                except Exception as e:
                    print(f"Failed to upload {local_file} to {remote_file}: {e}")

    sftp.close()
    return


def get_files_r(remote_path, sftp):
    files = {}
    for fileattr in sftp.listdir_attr(remote_path):
        if S_ISDIR(fileattr.st_mode):
            files.update(get_files_r(remote_path + "/" + fileattr.filename, sftp))
        else:
            files[fileattr.filename] = fileattr.st_size
    return files


def get_files(remote_path, results_dir, private_key_decrypted, machineID):
    ssh = paramiko.SSHClient()
    pkey = paramiko.RSAKey.from_private_key(StringIO(private_key_decrypted))
    machine_found = Machine.objects.get(id=machineID)
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(machine_found.fqdn, username=machine_found.user, pkey=pkey)
    sftp = ssh.open_sftp()

    # Check if the remote path is relative
    if not remote_path.startswith('/'):
        stdin, stdout, stderr = ssh.exec_command("echo $HOME")
        stdout = "".join(stdout.readlines()).strip()
        remote_path = stdout + "/" + remote_path

    files = get_files_r(remote_path, sftp)

    sftp.close()
    ssh.close()

    return files, remote_path


def https_to_ssh(git_url):
    if git_url.startswith("https://"):
        git_url = git_url.replace("https://", "")
        parts = git_url.split('/')
        if len(parts) < 2:
            raise ValueError("Invalid Git URL")

        domain = parts[0]
        user_repo = "/".join(parts[1:])
        return f"git@{domain}:{user_repo}.git"

    raise ValueError("URL is not in HTTPS format")


def get_github_code(repository, url, branch, local_folder):
    ssh_url = https_to_ssh(url)
    local_path = os.path.dirname(__file__)
    script_path = f"{local_path}/../scripts/git_clone.sh"
    try:
        result = subprocess.run(
            [script_path, repository, ssh_url, branch, local_folder],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        # Check the output
        if "Repository not found. Cloning repository..." in result.stdout:
            return True
        elif "Changes detected. Pulling latest changes..." in result.stdout:
            return True
        else:
            if result.stderr:
                print("Error:", result.stderr)
    except subprocess.CalledProcessError as e:
        print(f"Script execution failed with error code {e.returncode}: {e.stderr.decode('utf-8')}")
    except FileNotFoundError:
        print(f"Error: The script '{script_path}' was not found.")
    return False


def write_checkpoint_file(execution_folder, cmd2):
    script_path = f"{execution_folder}/checkpoint_script.sh"
    cmd = f'echo "{cmd2}" > {script_path} && chmod +x {script_path}'
    return cmd


def get_variables_exported(exported_variables):
    export_string = ""

    for key, value in exported_variables.items():
        export_string += f"export {key}={value}; "

    return export_string


def set_environment_variables(setup):
    exported_variables = {}

    if 'environment' in setup and isinstance(setup['environment'], dict):
        for key, value in setup['environment'].items():
            exported_variables[key] = value
    return exported_variables


def remove_numbers(input_str):
    # Split the input string by '.' to separate the hostname and domain
    parts = input_str.split('.')

    if len(parts) >= 2:
        # Take the first part as the hostname
        hostname = parts[0]
        if input_str.startswith("glogin"):
            return "mn5"
        # Remove any trailing digits from the hostname
        while hostname and hostname[-1].isdigit():
            hostname = hostname[:-1]

        return hostname
    else:
        # If there are not enough parts, return the original string
        return input_str


def extract_substring(s):
    match = re.search(r'([a-zA-Z]+)\d\.', s)
    if match:
        return match.group(1)
    return None


def get_file_extension(file_path):
    _, extension = os.path.splitext(file_path)
    return extension


def read_and_write_yaml(name):
    with open("documents/" + str(name)) as file:
        try:
            workflow = yaml.safe_load(file)
            return workflow
        except yaml.YAMLError as exc:
            print(exc)
    return None


def wdir_folder(principal_folder):
    uniqueIDfolder = uuid.uuid4()
    nameWdir = "execution_" + str(uniqueIDfolder)
    if not principal_folder.endswith("/"):
        principal_folder = principal_folder + "/"
    wdirDone = principal_folder + "" + nameWdir
    return wdirDone, nameWdir


def read_and_format_file(file_path):
    content = ""

    if file_path.endswith('.xlsx'):
        try:
            xls = pd.ExcelFile(file_path)
            content = ""
            for sheet_name in xls.sheet_names:
                df = pd.read_excel(file_path, sheet_name=sheet_name)
                content += f"<h2>{sheet_name}</h2>"
                content += df.to_html(index=False)
        except Exception as e:
            content = f"Failed to read XLSX: {e}"

    elif file_path.endswith('.csv'):
        try:
            df = pd.read_csv(file_path)
            content = df.to_html(index=False)
        except Exception as e:
            content = f"Failed to read CSV: {e}"
    else:
        try:
            with open(file_path, 'r') as file:
                content = file.read()
        except Exception as e:
            print("Error reading the file: ", e)

    return content


@login_required
def create_tool(request):
    sections = ['application', 'setup', 'slurm', 'COMPSs', 'environment']

    if request.method == 'POST':
        form = CreateToolForm(request.POST)
        errors = {}

        if form.is_valid():
            tool_name = form.cleaned_data['name']
            tool = Tool.objects.create(name=tool_name)
            field_names = set()

            custom_fields = {k: v for k, v in request.POST.items()
                             if k.startswith(f'custom_field_') or k.startswith('custom_boolean_field')}

            for field_key, field_name in custom_fields.items():
                if not field_name:
                    errors[field_key] = ['This field cannot be empty.']
                    tool.delete()
                    return render(request, 'pages/create_tool.html',
                                  {'form': form, 'errors': errors,
                                   'sections': sections})

                if field_name in field_names:
                    errors[field_key] = [
                        f'The field name "{field_name}" is duplicated. Please '
                        f'use unique names.']

                    return render(request, 'pages/create_tool.html', {
                        'form': form,
                        'errors': errors,
                        'tool': tool,
                        'existing_fields': tool.get_fields(),
                        'existing_repos': tool.get_repos(),
                        'sections': sections,
                    })
                field_names.add(field_name)
                if 'boolean' in field_key:
                    type = 'boolean'
                    index = field_key.split('_')[3]
                    section = request.POST.get(f'boolean_section_{index}',
                                                     None)
                    default_value = request.POST.get(f'default_value_boolean_'
                                                     f'{index}', None)
                    if default_value == "true":
                        default_value = True
                    elif default_value == "false":
                        default_value = False
                    preset_value = request.POST.get(f'preset_value_boolean_'
                                                    f'{index}', None)
                    if preset_value == "true":
                        preset_value = True
                    elif preset_value == "false":
                        preset_value = False
                else:
                    type = 'text'
                    index = field_key.split('_')[2]
                    default_value = request.POST.get(f'default_value_{index}',
                                                     None)
                    preset_value = request.POST.get(f'preset_value_{index}', None)
                    section = request.POST.get(f'section_{index}',
                                               None)
                if default_value == 'None' or default_value == '':
                    default_value = None
                if preset_value == 'None' or preset_value == '':
                    preset_value = None

                tool.add_field(field_name, default_value=default_value,
                               preset_value=preset_value, section=section,
                               type=type)

            modules = {k: v for k, v in request.POST.items()
                       if k.startswith(f'module_')}

            tool.set_modules_list(modules)

            tool.add_repo(request.POST.get('github_repo'),
                          request.POST.get('github_branch'),
                          request.POST.get('github_install') == 'on',
                          request.POST.get('github_install_dir'),
                          request.POST.get('github_editable') == 'on',
                          request.POST.get('github_requirements') == 'on',
                          request.POST.get('github_target') == 'on')

            additional_repos = {k: v for k, v in request.POST.items() if
                                k.startswith('github_repo_')}

            for repo_name, repo_value in additional_repos.items():
                if "branch" not in repo_name:
                    number = repo_name.split('github_repo_')[1]
                    tool.add_repo(repo_value,
                                  request.POST.get('github_branch_' + number),
                                  request.POST.get('github_install_' + number) == 'on',
                                  request.POST.get('github_install_dir_' + number),
                                  request.POST.get('github_editable_' + number) == 'on',
                                  request.POST.get('github_requirements_' + number) == 'on',
                                  request.POST.get('github_target_' + number) == 'on')

            request.session['tool_created'] = tool.name
            return redirect('tools')

        else:
            return render(request, 'pages/create_tool.html',
                          {'form': form, 'errors': form.errors, 'sections': sections})
    else:
        form = CreateToolForm()

        return render(request, 'pages/create_tool.html', {'form': form, 'sections': sections})


@login_required
def edit_tool(request, tool_name):
    tool = get_object_or_404(Tool, name=tool_name)
    errors = {}
    sections = ['application', 'setup', 'slurm', 'COMPSs', 'environment']

    if request.method == 'POST':

        form = CreateToolForm(request.POST, instance=tool)
        if form.is_valid():
            tool.name = form.cleaned_data['name']
            tool.save()

            tool.field_set.all().delete()
            additional_fields = {k: v for k, v in request.POST.items() if
                                 k.startswith('custom_field_') or
                                 k.startswith('custom_boolean_field')}

            field_names = set()
            for field_key, field_name in additional_fields.items():
                if not field_name:
                    errors[field_key] = ['This field cannot be empty.']
                    return render(request, 'pages/edit_tool.html', {
                        'form': form,
                        'errors': errors,
                        'tool': tool,
                        'existing_fields': tool.get_fields(),
                        'existing_repos': tool.get_repos(),
                        'sections': sections,
                    })

                if field_name in field_names:
                    errors[field_key] = [
                        f'The field name "{field_name}" is duplicated. Please '
                        f'use unique names.']
                    return render(request, 'pages/edit_tool.html', {
                        'form': form,
                        'errors': errors,
                        'tool': tool,
                        'existing_fields': tool.get_fields(),
                        'existing_repos': tool.get_repos(),
                        'sections': sections,
                    })
                field_names.add(field_name)

                if 'boolean' in field_key:
                    type = 'boolean'
                    index = field_key.split('_')[3]
                    section = request.POST.get(f'boolean_section_{index}',
                                               None)
                    default_value = request.POST.get(f'default_value_boolean_'
                                                     f'{index}', None)
                    if default_value == "true":
                        default_value = True
                    elif default_value == "false":
                        default_value = False
                    preset_value = request.POST.get(f'preset_value_boolean_'
                                                    f'{index}', None)
                    if preset_value == "true":
                        preset_value = True
                    elif preset_value == "false":
                        preset_value = False
                else:
                    type = 'text'
                    index = field_key.split('_')[2]
                    section = request.POST.get(f'section_{index}',
                                               None)
                    default_value = request.POST.get(f'default_value_{index}',
                                                     None)
                    preset_value = request.POST.get(f'preset_value_{index}',
                                                    None)

                if default_value == 'None' or default_value == '':
                    default_value = None
                if preset_value == 'None' or preset_value == '':
                    preset_value = None

                tool.add_field(field_name, default_value=default_value,
                               preset_value=preset_value, section=section,
                               type=type)

            modules = {k: v for k, v in request.POST.items()
                       if k.startswith(f'module_')}

            tool.set_modules_list(modules.values())

            tool.remove_repos()
            tool.add_repo(request.POST.get('github_repo'),
                          request.POST.get('github_branch'),
                          request.POST.get('github_install') == 'on',
                          request.POST.get('github_install_dir'),
                          request.POST.get('github_editable') == 'on',
                          request.POST.get('github_requirements') == 'on',
                          request.POST.get('github_target') == 'on')

            additional_repos = {k: v for k, v in request.POST.items() if k.startswith('github_repo_')}
            for repo_name, repo_value in additional_repos.items():
                if "branch" not in repo_name:
                    number = repo_name.split('github_repo_')[1]
                    branch = request.POST.get('github_branch_' + number)

                    tool.add_repo(repo_value,
                                  request.POST.get('github_branch_' + number),
                                  request.POST.get(
                                      'github_install_' + number) == 'on',
                                  request.POST.get('github_install_dir_' + number),
                                  request.POST.get(
                                      'github_editable_' + number) == 'on',
                                  request.POST.get(
                                      'github_requirements_' + number) == 'on',
                                  request.POST.get('github_target_' + number) == 'on')

                    if branch == '' or repo_value == '':
                        if branch == '':
                            errors['github_branch_' + number] = \
                                ['This field cannot be empty.']
                        else:
                            errors[repo_value] = \
                                ['This field cannot be empty.']
                        return render(request, 'pages/edit_tool.html', {
                            'form': form,
                            'errors': errors,
                            'tool': tool,
                            'existing_fields': tool.get_fields(),
                            'existing_repos': tool.get_repos(),
                            'sections': sections,
                        })
            request.session['tool_edited'] = tool_name
            tool.save()
            return redirect('tools')
        else:
            return render(request, 'pages/create_tool.html',
                          {'form': form, 'errors': form.errors,
                           'sections': sections})

    else:
        # Pass the custom fields and repos to the template for display
        existing_fields = tool.get_fields()
        existing_repos = tool.get_repos()
        existing_modules = tool.get_modules_list()

        first_repo = ''
        first_branch = ''
        for repo in existing_repos:
            first_repo = repo.url
            first_branch = repo.branch
            break

        form = CreateToolForm(instance=tool, initial={
            'github_repo': first_repo,
            'github_branch': first_branch
        })

        for f in existing_fields:
            if f.default_value is None:
                f.default_value = ''
            if f.preset_value is None:
                f.preset_value = ''

        return render(request, 'pages/edit_tool.html', {
            'form': form,
            'errors': errors,
            'tool': tool,
            'existing_fields': existing_fields,
            'existing_repos': existing_repos,
            'existing_modules': existing_modules,
            'sections': sections,
        })


