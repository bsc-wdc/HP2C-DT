import os
import random
import re
import shlex
import string
import subprocess
import threading
import time
import uuid
import yaml

from scp import SCPClient
from stat import S_ISDIR
from django.contrib.auth import authenticate, login, logout
from requests import RequestException
from django.views.decorators.csrf import csrf_exempt
from scripts.update_dashboards import update_dashboards, get_deployment_info
from .models import *
from django.contrib import messages
from django.shortcuts import render, redirect
from django.http import HttpResponse
from .forms import CategoricalDeviceForm, NonCategoricalDeviceForm, \
    CreateUserForm, Machine_Form, Key_Gen_Form, DocumentForm, ExecutionForm

import json
import requests
from django.contrib.auth.decorators import login_required
from cryptography.fernet import Fernet
import paramiko
from io import StringIO
from django.db.models import Q


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
            return render(request, 'pages/new_machine.html', {'form': form, 'flag': 'yes'})
        else:
            error_string = ""
            for field, errors in form.errors.items():
                error_string += f"{', '.join(errors)}\n"

            return render(request, 'pages/new_machine.html', {'form': form, 'error': True, 'message': error_string})

    else:
        form = Machine_Form(initial={'author': request.user})

    return render(request, 'pages/new_machine.html', {'form': form})


@login_required
def machines(request):
    # method to redefine the details of a Machine
    if request.method == 'POST':
        form = Machine_Form(request.POST)
        request.session['noMachines'] = "yes"
        if 'chooseButton' in request.POST:
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
            return render(request, 'pages/machines.html',
                          {'form': form,
                           'firstPhase': request.session['firstPhase'],
                           'noMachines': request.session['noMachines']})
        elif 'redefineButton' in request.POST:
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
                return render(request, 'pages/machines.html',
                              {'form': form,
                               'firstPhase': request.session['firstPhase'],
                               'flag': 'yes',
                               'noMachines': request.session[
                                   'noMachines']})
    else:
        form = Machine_Form()
        machines_done = populate_executions_machines(request)
        if not machines_done:
            request.session['noMachines'] = "no"
            request.session['firstPhase'] = "no"
        else:
            request.session['noMachines'] = "yes"
            request.session['firstPhase'] = "yes"

    return render(request, 'pages/machines.html',
                  {'form': form, 'machines': machines_done,
                   'noMachines': request.session['noMachines'],
                   'firstPhase': request.session['firstPhase']})


def populate_executions_machines(request):
    machines = Machine.objects.all().filter(author=request.user)
    machines_done = []
    if machines.count() != 0:
        for machine in machines:
            machines_done.append("" + str(machine.user) + "@" + machine.fqdn)
    return machines_done


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
    if request.method == 'POST':
        form = Key_Gen_Form(request.POST)
        if form.is_valid():
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
                return redirect('accounts:dashboard')
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
    return render(request, 'pages/ssh_keys.html',
                  {'form': form, 'warning': request.session['warning'], 'reuse_token': request.session['reuse_token'],
                   'machines': populate_executions_machines(request), 'check_existence_machines': request.session['check_existence_machines']})


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


@login_required
def tools(request):
    if request.method == 'POST':
        if 'disconnectButton' in request.POST:
            Connection.objects.filter(conn_id=request.session["conn_id"]).update(status="Disconnect")
            for key in list(request.session.keys()):
                if not key.startswith("_"):  # skip keys set by the django system
                    del request.session[key]
            return redirect("connections")
    else:
        stability_connection = check_stability_connection(request)
        if not stability_connection:
            return redirect('connections')
        squeue_list = squeue(request, request.session["machine_chosen"])
        return render(request, 'pages/tools.html',
                      {'check_connection_stable': "yes",
                       'machine_chosen': request.session['nameConnectedMachine'], 'squeue_list': squeue_list})


@login_required
def connections(request):
    if request.method == 'POST':
        if 'connection' in request.POST:
            user, fqdn = get_name_fqdn(request.POST.get('machineChoice'))
            machine_found = Machine.objects.get(author=request.user, user=user, fqdn=fqdn)
            machine_chosen = Key_Gen.objects.filter(machine_id=machine_found.id).get()
            private_key_encrypted = machine_chosen.private_key
            try:
                private_key_decrypted = decrypt(private_key_encrypted, request.POST.get("token")).decode()
            except Exception:
                machines_done = populate_executions_machines(request)
                request.session['check_existence_machines'] = "yes"
                return render(request, 'pages/connections.html',
                              {'machines': machines_done,
                               'check_existence_machines': request.session['check_existence_machines'],
                               "errorToken": 'yes'})

            request.session["private_key_decrypted"] = private_key_decrypted
            request.session['machine_chosen'] = machine_found.id
            c = Connection()
            c.user = request.user
            c.status = "Active"
            c.save()
            request.session["conn_id"] = c.conn_id
            stability_connection = check_stability_connection(request)
            if not stability_connection:
                machines_done = populate_executions_machines(request)
                if not machines_done:
                    request.session['check_existence_machines'] = "no"
                request.session["check_connection_stable"] = "Required"
                return render(request, 'pages/connections.html',
                              {'machines': machines_done, 'check_connection_stable': "no"})
            machine_connected = Machine.objects.get(id=request.session["machine_chosen"])
            request.session['nameConnectedMachine'] = "" + machine_connected.user + "@" + machine_connected.fqdn
            if request.session.get('redirect_to_run_sim', False):
                request.session.pop('redirect_to_run_sim')
                if 'original_request' in request.session:
                    request.method = 'POST'
                    request.POST = request.session.pop('original_request')
                    return redirect('run_sim')

            return redirect("tools")

    machines_done = populate_executions_machines(request)
    request.session["check_connection_stable"] = "no"
    request.session["check_existence_machines"] = "yes"
    show_warning = request.session.get('redirect_to_run_sim', False)

    if not machines_done:
        request.session['check_existence_machines'] = "no"


    stability_connection = check_stability_connection(request)
    if stability_connection:
        return redirect("tools")

    return render(request, 'pages/connections.html',
                  {'machines': machines_done,
                   'check_existence_machines': request.session['check_existence_machines'],
                   'show_warning': show_warning
                   })


def squeue(request, machineID):
    ssh = connection_ssh(request.session["private_key_decrypted"], machineID)
    stdin, stdout, stderr = ssh.exec_command("squeue")
    stdout = stdout.readlines()
    return stdout


def get_name_fqdn(machine):
    user = machine.split("@")[0]
    fqdn = machine.split("@")[1]
    return user, fqdn


def check_stability_connection(request):
    conn_id = request.session.get('conn_id')
    if conn_id != None:
        conn = Connection.objects.get(conn_id=request.session["conn_id"])
        if conn.status == "Disconnect":
            return False
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
@login_required
def run_sim(request):
    if request.method == 'POST':
        check_conn_bool = checkConnection(request)
        if not check_conn_bool:
            request.session['original_request'] = request.POST
            request.session['redirect_to_run_sim'] = True
            return redirect('connections')

        form = DocumentForm(request.POST, request.FILES)
        if form.is_valid():
            branch = request.POST.get('branchChoice')
            for filename, file in request.FILES.items():
                unique_id = uuid.uuid4()
                name_e = ((str(file).split(".")[0]) + "_" + str(unique_id) + "."
                          + str(file).split(".")[1])
            name = name_e
            document = form.save(commit=False)
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
            e_id = start_exec(num_nodes, name_sim, exec_time, qos, name, request,
                              auto_restart_bool, checkpoint_bool, d_bool,
                              t_bool, g_bool, branch)
            run_sim = run_sim_async(request, name, num_nodes, name_sim,
                                    exec_time, qos, checkpoint_bool,
                                    auto_restart_bool, e_id, branch, g_bool,
                                    t_bool, d_bool, project_name)
            run_sim.start()
            return redirect('executions')

    else:
        form = DocumentForm()
        request.session['flag'] = 'first'
        branches = get_github_repo_branches()
        check_conn_bool = checkConnection(request)
        if not check_conn_bool:
            request.session['original_request'] = request.POST
            request.session['redirect_to_run_sim'] = True
            return redirect('connections')
        return render(request, 'pages/run_simulation.html',
                      {'form': form, 'flag': request.session['flag'],
                       'machines': populate_executions_machines(request),
                       'machine_chosen': request.session['nameConnectedMachine'],
                       'branches': branches})


def checkConnection(request):
    conn_id = request.session.get('conn_id')
    if conn_id != None:
        conn = Connection.objects.get(conn_id=request.session["conn_id"])
        if conn.status == "Disconnect":
            return False
        return True
    return False


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
    repo_url = "https://github.com/CAELESTIS-Project-EU/Workflows"
    # Extract the user/repo from the URL
    user_repo = repo_url.split("github.com/")[1]
    # GitHub API endpoint to get branches
    api_url = f"https://api.github.com/repos/{user_repo}/branches"

    # Make the API request
    response = requests.get(api_url)

    # Check if the response is successful
    if response.status_code == 200:
        branches = response.json()
        return [branch['name'] for branch in branches]
    else:
        return f"Error: Unable to access the GitHub repository. Status code: {response.status_code}"


def start_exec(num_nodes, name_sim, execTime, qos, name, request, auto_restart_bool, checkpoint_bool, d_bool, t_bool,
               g_bool, branch):
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
    form.save()
    return uID

@login_required
def executions(request):
    if request.method == 'POST':
        if 'resultExecution' in request.POST:
            request.session['jobIDdone'] = request.POST.get("resultExecutionValue")
            return redirect('results')
        elif 'failedExecution' in request.POST:
            request.session['jobIDfailed'] = request.POST.get("failedExecutionValue")
            return redirect('execution_failed')
        elif 'infoExecution' in request.POST:
            request.session['eIDinfo'] = request.POST.get("infoExecutionValue")
            return redirect('info_execution')
        elif 'timeoutExecution' in request.POST:
            request.session['jobIDcheckpoint'] = request.POST.get(
                "timeoutExecutionValue")
            checkpointing_noAutorestart(
                request.POST.get("timeoutExecutionValue"), request)
            return redirect('executions')
        elif 'stopExecution' in request.POST:
            request.session['stopExecutionValue'] = request.POST.get("stopExecutionValue")
            stopExecution(request.POST.get("stopExecutionValue"), request)
        elif 'deleteExecution' in request.POST:
            request.session['deleteExecutionValue'] = request.POST.get("deleteExecutionValue")
            deleteExecution(request.POST.get("deleteExecutionValue"), request)
        elif 'run_sim' in request.POST:
            request.session['machine_chosen'] = get_id_from_string(request.POST.get("machine_chosen_value"),
                                                                   request.user)
            return redirect('run_sim')

        elif 'disconnectButton' in request.POST:
            global dict_thread
            Connection.objects.filter(idConn_id=request.session["idConn"]).update(status="Disconnect")
            for key in list(request.session.keys()):
                if not key.startswith("_"):  # skip keys set by the django system
                    del request.session[key]
            machines_done = populate_executions_machines(request)
            if not machines_done:
                request.session['firstCheck'] = "no"
            request.session["checkConn"] = "Required"
            request.session['machine_chosen'] = None
            return render(request, 'pages/executions.html',
                          {'machines': machines_done, 'checkConn': "no"})

        checkConnBool = checkConnection(request)
        if not checkConnBool:
            machines_done = populate_executions_machines(request)
            if not machines_done:
                request.session['firstCheck'] = "no"
            request.session["checkConn"] = "Required"
            return render(request, 'pages/executions.html',
                          {'machines': machines_done, 'checkConn': "no"})
        machine_connected = Machine.objects.get(id=request.session["machine_chosen"])
        executions = Execution.objects.all().filter(author=request.user, machine=machine_connected).filter(
            Q(status="PENDING") | Q(status="RUNNING") | Q(status="INITIALIZING"))
        executionsDone = Execution.objects.all().filter(author=request.user, status="COMPLETED",
                                                        machine=machine_connected)
        executionsFailed = Execution.objects.all().filter(author=request.user, status="FAILED",
                                                          machine=machine_connected)
        executionTimeout = Execution.objects.all().filter(author=request.user, status="TIMEOUT", checkpointBool=True,
                                                          machine=machine_connected)
        executionsCheckpoint = Execution.objects.all().filter(author=request.user, status="TIMEOUT",
                                                              autorestart=True, machine=machine_connected)
        executionsCanceled = Execution.objects.all().filter(author=request.user, status="CANCELLED+",
                                                            checkpoint="-1", machine=machine_connected)
        request.session['nameConnectedMachine'] = "" + machine_connected.user + "@" + machine_connected.fqdn
        for execution in executionsCanceled:
            checks = Execution.objects.all().get(author=request.user, status="CANCELLED+", checkpoint=execution.jobID,
                                                 machine=machine_connected)
            if checks is not None:
                execution.status = "TIMEOUT"
                execution.checkpoint = 0
                execution.save()
        return render(request, 'pages/executions.html',
                      {'executions': executions, 'executionsDone': executionsDone,
                       'executionsFailed': executionsFailed,
                       'executionsTimeout': executionTimeout, 'checkConn': "yes",
                       'machine_chosen': request.session['nameConnectedMachine']})
    else:
        form = ExecutionForm()
        machines_done = populate_executions_machines(request)
        if not machines_done:
            request.session['firstCheck'] = "no"
            return render(request, 'pages/executions.html',
                          {'firstCheck': request.session['firstCheck']})
        elif "private_key_decrypted" not in request.session:
            request.session['firstCheck'] = "yes"
            request.session["checkConn"] = "no"
            return render(request, 'pages/executions.html',
                          {'form': form, 'machines': machines_done,
                           'checkConn': request.session["checkConn"],
                           'firstCheck': request.session['firstCheck']})
        else:
            checkConnBool = checkConnection(request)
            if not checkConnBool:
                machines_done = populate_executions_machines(request)
                if not machines_done:
                    request.session['firstCheck'] = "no"
                request.session["checkConn"] = "Required"
                return render(request, 'pages/executions.html',
                              {'machines': machines_done, 'checkConn': "no",
                               'machine_chosen': request.POST.get('machineChoice')})

            machine_connected = Machine.objects.get(id=get_machine(request))
            request.session['nameConnectedMachine'] = "" + machine_connected.user + "@" + machine_connected.fqdn
            executions = Execution.objects.all().filter(author=request.user, machine=machine_connected).filter(
                Q(status="PENDING") | Q(status="RUNNING") | Q(status="INITIALIZING"))
            executionsDone = Execution.objects.all().filter(author=request.user, machine=machine_connected,
                                                            status="COMPLETED")
            executionsFailed = Execution.objects.all().filter(author=request.user, machine=machine_connected,
                                                              status="FAILED")
            executionsCheckpoint = Execution.objects.all().filter(author=request.user, machine=machine_connected,
                                                                  status="TIMEOUT")
            executionTimeout = Execution.objects.all().filter(author=request.user, machine=machine_connected,
                                                              status="TIMEOUT",
                                                              autorestart=False, checkpointBool=True)
            executionsCanceled = Execution.objects.all().filter(author=request.user, machine=machine_connected,
                                                                status="CANCELED",
                                                                checkpoint="-1")
            for execution in executionsCanceled:
                checks = Execution.objects.all().get(author=request.user, status="CANCELLED+",
                                                     machine=machine_connected,
                                                     checkpoint=execution.jobID)
                if checks is not None:
                    execution.status = "TIMEOUT"
                    execution.checkpoint = 0
                    execution.save()
                checks.delete()
            request.session["checkConn"] = "yes"
    return render(request, 'pages/executions.html',
                  {'form': form, 'executions': executions, 'executionsDone': executionsDone,
                   'executionsFailed': executionsFailed, 'executionsTimeout': executionTimeout,
                   "checkConn": request.session["checkConn"],
                   'machine_chosen': request.session['nameConnectedMachine']})


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


def get_machine(request):
    return Machine.objects.get(id=request.session['machine_chosen']).id


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
    ssh = connection_ssh(request.session["private_key_decrypted"], machineID)
    executions = Execution.objects.all().filter(author=request.user, machine=request.session['machine_chosen']).filter(
        Q(status="PENDING") | Q(status="RUNNING") | Q(status="INITIALIZING"))
    for executionE in executions:
        if executionE.jobID != 0:
            stdin, stdout, stderr = ssh.exec_command(
                "sacct -j " + str(executionE.jobID) + " --format=jobId,user,nnodes,elapsed,state | sed -n 3,3p")
            stdout = stdout.readlines()
            values = str(stdout).split()
            Execution.objects.filter(jobID=executionE.jobID).update(time=values[3])
            if str(values[4]) == "COMPLETED" and executionE.status != "COMPLETED":
                Execution.objects.filter(jobID=executionE.jobID).update(status=values[4], time=values[3],
                                                                        nodes=int(values[2]))
                results_path = "results"
                local_folder_path = os.path.join(executionE.wdir, results_path)
            if not (str(values[4]) == "FAILED" and executionE.status == "INITIALIZING"):
                Execution.objects.filter(jobID=executionE.jobID).update(status=values[4], time=values[3],
                                                                        nodes=int(values[2]))
    return True


def get_last_subdirectory(url):
    # Split the URL by '/' and get the last element
    return url.rstrip('/').split('/')[-1]


def remove_protocol_and_domain(url):
    # Remove protocol and domain
    return re.sub(r'^.*?//[^/]+/', '', url)


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


def render_right(request):
    checkConnBool = checkConnection(request)
    if not checkConnBool:
        machines_done = populate_executions_machines(request)
        if not machines_done:
            request.session['firstCheck'] = "no"
        request.session["checkConn"] = "Required"
        return render(request, 'pages/connections.html',
                      {'machines': machines_done, 'checkConn': "no"})
    return


class updateExecutions(threading.Thread):
    def __init__(self, request, connectionID):
        threading.Thread.__init__(self)
        self.request = request
        self.timeout = 120 * 60
        self.connectionID = connectionID

    def run(self):
        timeout_start = time.time()
        while time.time() < timeout_start + self.timeout:
            conn = Connection.objects.get(idConn_id=self.connectionID)
            if conn.status == "Disconnect":
                break
            boolException = update_table(self.request)
            if not boolException:
                break
            time.sleep(5)
        Connection.objects.filter(idConn_id=self.connectionID).update(status="Disconnect")
        render_right(self.request)
        return


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
        machine_found = Machine.objects.get(id=self.request.session['machine_chosen'])
        fqdn = machine_found.fqdn
        machine_folder = extract_substring(fqdn)
        userMachine = machine_found.user
        principal_folder = machine_found.wdir #folder specified as workind dir in the online service
        wdirPath, nameWdir = wdir_folder(principal_folder)
        #command to create all the folders and copy the yaml file passed through the service
        setup_path = f"{principal_folder}/{nameWdir}/setup/{str(self.name)}"
        cmd1 = (
            f"source /etc/profile; mkdir -p {principal_folder}/{nameWdir}/setup/; "
            f"echo {shlex.quote(str(setup))} > {setup_path};"
            f"cd {principal_folder}; BACKUPDIR=$(ls -td ./*/ | head -1); echo EXECUTION_FOLDER:$BACKUPDIR;")
        print(f"cmd1 : {cmd1}")

        ssh = connection_ssh(self.request.session["private_key_decrypted"], machine_found.id)

        stdin, stdout, stderr = ssh.exec_command(cmd1)

        execution_folder = wdirPath + "/execution"
        setup_folder = wdirPath + "/setup"

        Execution.objects.filter(eID=self.eiD).update(wdir=execution_folder,
                                                      setup_path=setup_folder)

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
        home_path = os.getenv("HOME")
        local_folder = os.path.join(home_path, "ui-hp2cdt", "installDir")
        scp_upload_code_folder(local_folder, path_install_dir,
                               self.request.session["private_key_decrypted"],
                               machine_found.id, self.branch,
                               repository="datagen", local_folder=local_folder)
        scp_upload_code_folder(local_folder, path_install_dir_stability_analysis,
                               self.request.session["private_key_decrypted"],
                               machine_found.id, stability_analysis_branch,
                               repository="stability_analysis",
                               local_folder=local_folder)
        scp_upload_code_folder(local_folder,
                               path_install_dir_gridcal,
                               self.request.session["private_key_decrypted"],
                               machine_found.id, gridcal_branch,
                               repository="new-GridCal",
                               local_folder=local_folder)

        exported_variables = set_environment_variables(setup)

        if self.checkpoint_bool:
            cmd2 = (
                f"source /etc/profile; source {path_install_dir}/scripts/load.sh "
                f"{path_install_dir} {path_install_dir_stability_analysis} {path_install_dir_gridcal} {machine_name} {machine_node}; "
                f"{get_variables_exported(exported_variables)} mkdir -p {execution_folder}; "
                f"cd {path_install_dir}/scripts/{machine_name}/; source app-checkpoint.sh {userMachine} {str(self.name)} {setup_folder} "
                f"{execution_folder} {self.numNodes} {self.execTime} {self.qos} {machine_found.installDir} {self.branch} {machine_found.dataDir} "
                f"{self.gOPTION} {self.tOPTION} {self.dOPTION} {self.project_name};")
            cmd_writeFile_checkpoint = (
                f"source /etc/profile; source {path_install_dir}/scripts/load.sh "
                f"{path_install_dir} {path_install_dir_stability_analysis} {path_install_dir_gridcal} {machine_name} {machine_node}; "
                f"{get_variables_exported(exported_variables)} cd {path_install_dir}/scripts/{machine_name}/; "
                f"source app-checkpoint.sh {userMachine} {str(self.name)} {setup_folder} {execution_folder} {self.numNodes} "
                f"{self.execTime} {self.qos} {machine_found.installDir} {self.branch} {machine_found.dataDir} {self.gOPTION} "
                f"{self.tOPTION} {self.dOPTION} {self.project_name};")
            cmd2 += write_checkpoint_file(execution_folder,
                                          cmd_writeFile_checkpoint)
        else:
            cmd2 = (
                f"source /etc/profile; source {path_install_dir}/scripts/load.sh "
                f"{path_install_dir} {path_install_dir_stability_analysis} {path_install_dir_gridcal} {machine_name} {machine_node}; "
                f"{get_variables_exported(exported_variables)} mkdir -p {execution_folder}; "
                f"cd {path_install_dir}/scripts/{machine_name}/; source app.sh {userMachine} {str(self.name)} {setup_folder} "
                f"{execution_folder} {self.numNodes} {self.execTime} {self.qos} {path_install_dir} {self.branch} {machine_found.dataDir} "
                f"{self.gOPTION} {self.tOPTION} {self.dOPTION} {self.project_name};")
        print(f"run_sim : {cmd2} ")

        stdin, stdout, stderr = ssh.exec_command(cmd2)
        stdout = stdout.readlines()
        stderr = stderr.readlines()
        print("---------------------------")
        for s in stdout:
            print(s)
        print("---------------------------")


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


def scp_upload_code_folder(local_path, remote_path, private_key_decrypted, machineID, branch,
                           repository, local_folder):
    res = get_github_code(repository, branch, local_folder)
    ssh = paramiko.SSHClient()
    pkey = paramiko.RSAKey.from_private_key(StringIO(private_key_decrypted))
    machine_found = Machine.objects.get(id=machineID)
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(machine_found.fqdn, username=machine_found.user, pkey=pkey)
    sftp = ssh.open_sftp()

    # Check and create remote folder if it doesn't exist
    remote_dirs = remote_path.split('/')
    current_dir = ''
    emptyDir = False
    for dir in remote_dirs:
        if dir:
            current_dir += '/' + dir
            try:
                sftp.stat(current_dir)
            except FileNotFoundError:
                print("FileNotFoundError " + str(current_dir))
                sftp.mkdir(current_dir)
                emptyDir = True
    if res or emptyDir:
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


def get_github_code(repository, branch_name, local_folder):
    local_path = os.path.dirname(__file__)
    script_path = f"{local_path}/../scripts/git_clone_{repository}.sh"
    try:
        result = subprocess.run([script_path, branch_name, local_folder], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
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

    # Extract and set environment variables
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


def  extract_substring(s):
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