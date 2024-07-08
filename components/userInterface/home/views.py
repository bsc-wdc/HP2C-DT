import os

from django.contrib.auth import authenticate, login, logout
from requests import RequestException
from django.views.decorators.csrf import csrf_exempt
from scripts.update_dashboards import update_dashboards, get_deployment_info
from .models import *
from django.contrib import messages
from django.shortcuts import render, redirect
from django.http import HttpResponse
from .forms import CategoricalDeviceForm, NonCategoricalDeviceForm, \
    CreateUserForm, Machine_Form, Key_Gen_Form
from core import settings
import json
import requests
from django.contrib.auth.decorators import login_required
from cryptography.fernet import Fernet
import paramiko
from io import StringIO



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
            instance = form.save(commit=False)
            instance.author = request.user
            instance.save()
            return render(request, 'pages/new_machine.html',
                          {'form': form, 'flag': 'yes'})
        else:
            error_string = ""
            for field, errors in form.errors.items():
                error_string += f"{', '.join(errors)}\n"

            return render(request, 'pages/new_machine.html',
                          {'form': form, 'error': True,
                           'message': error_string})

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
            request.session['firstCheck'] = "yes"
        else:
            request.session['firstCheck'] = "no"
    return render(request, 'pages/ssh_keys.html',
                  {'form': form, 'warning': request.session['warning'], 'reuse_token': request.session['reuse_token'],
                   'machines': populate_executions_machines(request), 'firstCheck': request.session['firstCheck']})


def encrypt(message: bytes, key: bytes) -> bytes:
    return Fernet(key).encrypt(message)


def decrypt(token: bytes, key: bytes) -> bytes:
    try:
        res = Fernet(key).decrypt(token)
    except Exception as e:
        log.error("Error decrypting token: %s", str(e))
        raise
    return res

@login_required
def ssh_keys_generation(request):
    if request.method == 'POST':
        return render('/')
    else:
        return render('/')


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
