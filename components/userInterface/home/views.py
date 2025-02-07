import os
import re

from django.contrib.auth import authenticate, login, logout
from django.views.decorators.csrf import csrf_exempt
from django.core.exceptions import ObjectDoesNotExist
from scripts.update_dashboards import update_dashboards
from .classes import *
from .dashboard import *
from .execution import *
from .file_management import *
from .models import *
from django.contrib import messages
from django.shortcuts import render, redirect, get_object_or_404
from django.http import HttpResponse, Http404
from .forms import (CategoricalDeviceForm, NonCategoricalDeviceForm, \
                    CreateUserForm, Machine_Form, Key_Gen_Form, DocumentForm,
                    ExecutionForm,
                    CreateToolForm)
import requests
from django.contrib.auth.decorators import login_required
from cryptography.fernet import Fernet
import paramiko
from io import StringIO
from django.db.models import Q

from .ssh import encrypt, decrypt, get_connection_status, get_name_fqdn, \
    check_connection, connection_ssh, populate_executions_machines, \
    get_machine_connected
from .tool import extract_tool_data, get_form_from_tool, tool_to_yaml, \
    yaml_to_tool


@login_required
def edge_detail(request, edge_name):
    """
    View for displaying edge info (live chart and graph)

    :param request: request
    :param edge_name: Name of the edge involved
    :return: HTML render
    """
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
            alerts_link = deployment.alerts_link
        except Exception as e:
            print(e)
        script_content = geomap(server_port, server_url)
        return render(request, 'pages/index.html',
                      {'edgeDevices': edgeDevices, 'forms': forms,
                       'script_content': script_content,
                       'alerts_link': alerts_link})


@csrf_exempt
@login_required
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
    """
    View for creating new machine.

    :param request: request
    :return: HTML render
    """
    if request.method == 'POST':
        form = Machine_Form(request.POST)
        form.data = form.data.copy()
        form.data['author'] = request.user

        if form.is_valid():
            user = form.cleaned_data.get('user')
            fqdn = form.cleaned_data.get('fqdn')
            author = form.cleaned_data.get('author')

            if Machine.objects.filter(author=author, user=user,
                                      fqdn=fqdn).exists():
                error_string = 'A machine with this author, user and FQDN already exists.'
                return render(request, 'pages/new_machine.html',
                              {'form': form, 'error': True,
                               'message': error_string})

            instance = form.save(commit=False)
            instance.author = request.user
            instance.save()
            request.session['machine_created'] = f"{user}@{fqdn}"
            return redirect('hpc_machines')
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


##################### USERS #########################
def login_page(request):
    """
    Login view

    :param request: request
    :return: HTML render
    """
    if request.method == 'POST':
        username = request.POST['username']
        password = request.POST['password1']
        # if is_recaptcha_valid(request):
        user = authenticate(request, username=username, password=password)
        if user is not None:
            form = login(request, user)
            return redirect('dashboard')
        else:
            form = CreateUserForm()
            return render(request, 'pages/login_page.html',
                          {'form': form, 'error': True})
    else:
        form = CreateUserForm()
    return render(request, 'pages/login_page.html', {'form': form})


def register_page(request):
    """
    Register page view

    :param request: request
    :return: HTML render
    """
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
    """
    Auxiliary view used for logging out

    :param request: request
    :return: redirect to login page
    """
    logout(request)
    messages.info(request, "Logged out successfully!")
    return redirect("login")


######################## SSH KEYS ####################
@login_required
def ssh_keys(request):
    """
    View used for creating ssh keys. This will reuse the same keys for every
    login node within a machine.

    :param request: request
    :return: HTML render
    """
    machine_connected = get_machine_connected(request)
    if request.method == 'POST':
        form = Key_Gen_Form(request.POST)
        if form.is_valid():
            Connection.objects.filter(user=request.user).update(
                status="Disconnect")
            if 'reuse_token_button' in request.POST:  # if the user has more than 1 Machine, he can decide to use the same SSH keys and token for all its machines
                machine = request.POST.get('machineChoice')
                user = machine.split("@")[0]
                fqdn = machine.split("@")[1]
                machine_found = Machine.objects.get(author=request.user,
                                                    user=user, fqdn=fqdn)
                instance = form.save(commit=False)
                instance.author = request.user
                instance.machine = machine_found
                instance.public_key = Key_Gen.objects.get(
                    author=instance.author).public_key
                instance.private_key = Key_Gen.objects.get(
                    author=instance.author).private_key
                instance.save()
                request.session['warning'] = "first"
                return redirect('dashboard')
            else:  # normal generation of the SSH keys
                instance = form.save(commit=False)
                instance.author = request.user
                machine = request.POST.get(
                    'machineChoice')  # it's the machine choosen by the user
                user = machine.split("@")[0]
                fqdn = machine.split("@")[1]
                request.userMachine = user
                request.fqdn = fqdn
                machine_found = Machine.objects.get(author=request.user,
                                                    user=user, fqdn=fqdn)
                instance.machine = machine_found
                token = Fernet.generate_key()  # to generate a security token
                key = paramiko.RSAKey.generate(
                    2048)  # to generate the SSH keys
                privateString = StringIO()
                key.write_private_key(privateString)
                private_key = privateString.getvalue()
                x = private_key.split("\'")
                private_key = x[0]
                public_key = key.get_base64()
                enc_private_key = encrypt(private_key.encode(),
                                          token)  # encrypting the private SSH keys using the security token, only the user is allowed to use its SSH keys to connect to its machine
                enc_private_key = str(enc_private_key).split("\'")[1]
                x = str(token).split("\'")
                token = x[1]
                instance.public_key = public_key
                instance.private_key = enc_private_key
                if Key_Gen.objects.filter(author=instance.author,
                                          machine=instance.machine).exists():
                    if request.session['warning'] == "first":
                        if (Key_Gen.objects.filter(
                                author=instance.author).count() > 1):
                            request.session['warning'] = "third"
                            return render(request, 'pages/ssh_keys.html',
                                          {'form': form,
                                           'warning': request.session[
                                               'warning'],
                                           'machines': populate_executions_machines(
                                               request)})
                        else:
                            request.session['warning'] = "second"
                            return render(request, 'pages/ssh_keys.html',
                                          {'form': form,
                                           'warning': request.session[
                                               'warning'],
                                           'machines': populate_executions_machines(
                                               request)})

                    if (Key_Gen.objects.filter(
                            author=instance.author).count() > 1):
                        Key_Gen.objects.filter(author=instance.author).update(
                            public_key=instance.public_key,
                            private_key=instance.private_key)

                    else:
                        Key_Gen.objects.filter(author=instance.author,
                                               machine=instance.machine).update(
                            public_key=instance.public_key,
                            private_key=instance.private_key)
                elif (Key_Gen.objects.filter(author=instance.author).exists()):
                    if request.session['reuse_token'] == "no":
                        request.session['reuse_token'] = "yes"
                        request.session['warning'] = "first"
                        machine = request.POST.get('machineChoice')
                        return render(request, 'pages/ssh_keys.html',
                                      {'form': form,
                                       'warning': request.session['warning'],
                                       'reuse_token': request.session[
                                           'reuse_token'],
                                       'machines': populate_executions_machines(
                                           request), 'choice': machine})
                else:
                    instance.save()
                public_key = "rsa-sha2-512 " + public_key
                return render(request, 'pages/ssh_keys_generation.html',
                              {'token': token, 'public_key': public_key})
    else:
        form = Key_Gen_Form(initial={'public_key': 123, 'private_key': 123})
        request.session['reuse_token'] = "no"
        request.session['warning'] = "first"
        if not populate_executions_machines(request):
            request.session['check_existence_machines'] = "yes"
        else:
            request.session['check_existence_machines'] = "no"

    context = {'form': form, 'warning': request.session['warning'],
               'reuse_token': request.session['reuse_token'],
               'machines': populate_executions_machines(request),
               'check_existence_machines': request.session[
                   'check_existence_machines']}

    if machine_connected is not None:
        machine_connected = "" + machine_connected.user + "@" + machine_connected.fqdn
        context['machine_connected'] = machine_connected

    return render(request, 'pages/ssh_keys.html', context)


@login_required
def ssh_keys_generation(request):
    if request.method == 'POST':
        return render('/')
    else:
        return render('/')


@login_required
def tools(request):
    """
    Display executions log and the forms of the existing tools. It is required
    to be connected to a machine.

    :param request: request
    :return: HTML render
    """
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

            match = re.match(rf'^getYaml(.*)$', key)
            if bool(match):
                tool_name = match.group(1)
                return redirect('download_yaml', tool_name=tool_name)

            match = re.match(rf'^cloneTool(.*)$', key)
            if bool(match):
                tool_name = match.group(1)
                new_name = request.POST.get(f"new_tool_name_{tool_name}")

                tool = Tool.objects.get(name=tool_name)

                cloned_tool = Tool.objects.create(name=new_name,
                                                  modules_list=tool.modules_list,
                                                  use_args=tool.use_args)

                original_fields = tool.field_set.all()
                for field in original_fields:
                    Field.objects.create(
                        name=field.name,
                        default_value=field.default_value,
                        preset_value=field.preset_value,
                        section=field.section,
                        tool=cloned_tool,
                        type=field.type
                    )

                original_repos = tool.repo_set.all()
                for repo in original_repos:
                    Repo.objects.create(
                        url=repo.url,
                        branch=repo.branch,
                        install=repo.install,
                        install_dir=repo.install_dir,
                        editable=repo.editable,
                        requirements=repo.requirements,
                        target=repo.target,
                        tool=cloned_tool
                    )

                return redirect('tools')

        if 'resultExecution' in request.POST:
            request.session['eIDdone'] = request.POST.get(
                "resultExecutionValue")
            request.session['jobIDdone'] = request.POST.get("jobIDValue")
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
        return redirect('tools')
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
        run_job = request.session.pop('run_job', None)
        tool_created = request.session.pop('tool_created', None)
        tool_edited = request.session.pop('tool_edited', None)
        tool_deleted = request.session.pop('tool_deleted', None)
        initializing = request.session.pop('initializing', None)

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
            'initializing': initializing
        })


@login_required
def hpc_machines(request):
    """
    Displays the existing machines (for a concrete user) and allows a
    connection to one of them if the proper token is provided

    :param request: request
    :return: HTML render
    """
    machines_done = populate_executions_machines(request)
    stability_connection = check_connection(request)
    if request.method == 'POST':
        form = Machine_Form(request.POST)

        if 'connectButton' in request.POST:
            user, fqdn = get_name_fqdn(request.POST.get('machineChoice'))
            machine_found = Machine.objects.get(author=request.user, user=user,
                                                fqdn=fqdn)
            try:
                machine_chosen = Key_Gen.objects.filter(
                    machine_id=machine_found.id).get()
            except ObjectDoesNotExist:
                request.session['ssh_keys_not_created'] = True
                return redirect('hpc_machines')

            private_key_encrypted = machine_chosen.private_key
            try:
                private_key_decrypted = decrypt(private_key_encrypted,
                                                request.POST.get(
                                                    "token")).decode()
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
            connection, created = Connection.objects.get_or_create(
                user=request.user)

            if connection.status == "Active" or connection.status == "Pending" or connection.status == "Failed":
                return redirect('hpc_machines')
            connection.machine = machine_found
            connection.status = 'Pending'
            connection.save()

            request.session["conn_id"] = connection.conn_id
            threadUpdate = updateExecutions(request, connection.conn_id)
            threadUpdate.start()
            stability_connection = check_connection(request)
            str_machine_connected = None
            if stability_connection:
                machine_connected = Machine.objects.get(
                    id=request.session["machine_chosen"])
                str_machine_connected = str(
                    machine_connected.user) + "@" + machine_connected.fqdn
                request.session[
                    'nameConnectedMachine'] = "" + machine_connected.user + "@" + machine_connected.fqdn
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
            status = get_connection_status(request)
            request.session['firstPhase'] = "yes"
            return render(request, 'pages/hpc_machines.html',
                          {'machines': machines_done, 'form': form,
                           'firstPhase': request.session['firstPhase'],
                           'check_existence_machines': request.session[
                               'check_existence_machines'],
                           'show_connected': str_machine_connected,
                           'connected': stability_connection,
                           'status': status
                           })

        if 'disconnectButton' in request.POST:
            status = get_connection_status(request)
            if status == "Disconnect":
                return redirect("hpc_machines")
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
                               'connected': stability_connection,
                               'status': status
                               })
            Connection.objects.filter(user=request.user).update(
                status="Disconnect", machine=None)
            status = get_connection_status(request)
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
                           'show_disconnected': str_machine_disconnected,
                           'status': status
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
                Machine.objects.filter(
                    id=request.session['machineID']).update(user=userForm,
                                                            fqdn=fqdnForm)
                request.session['firstPhase'] = "yes"
                request.session['machine_created'] = f"{userForm}@{fqdnForm}"
                return redirect('hpc_machines')
    else:
        status = get_connection_status(request)

        if status == "Failed":
            Connection.objects.filter(user=request.user).update(
                status="Disconnect")

        form = Machine_Form()
        request.session["check_connection_stable"] = "no"
        request.session["check_existence_machines"] = "yes"
        show_warning = (status != "Active" and
                        (request.session.get('redirect_to_run_sim', False) or
                         request.session.get('redirect_to_tools', False)))

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
                       'check_existence_machines': request.session[
                           'check_existence_machines'],
                       'show_warning': show_warning,
                       'connected': stability_connection,
                       'machine_created': machine_created,
                       'ssh_keys_error': ssh_keys_error,
                       'tool_created': tool_created, 'status': status})


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


@login_required
def results(request):
    """
    Displays the result of a concrete execution (with a concrete eID).
    Allows the user to download the log files.

    :param request: request
    :return: HTML render
    """
    eID = request.session['eIDdone']
    jobID = request.session['jobIDdone']
    remote_files = {}
    local_files = {}
    remote_path = None
    execUpdate = Execution.objects.get(eID=eID) or None
    try:
        ssh = connection_ssh(request.session['private_key_decrypted'],
                             request.session['machine_chosen'])
        stdin, stdout, stderr = ssh.exec_command(
            "sacct -j " + str(
                jobID) + " --format=jobId,user,nnodes,elapsed,state | sed -n 3,3p")
        stdout = stdout.readlines()
        print(stdout)
        values = str(stdout).split()
        Execution.objects.filter(jobID=jobID).update(status=values[4],
                                                     time=values[3],
                                                     nodes=int(values[2]))
        execUpdate = Execution.objects.get(jobID=jobID)
        remote_path = execUpdate.wdir
        remote_files = get_files(remote_path,
                                 request.session['private_key_decrypted'],
                                 request.session['machine_chosen'])
        remote_files = dict(sorted(remote_files.items()))
    except:
        print(
            "Warning: unable to get remote files. The execution may not started")

    try:
        # Get UI logs
        dir_name = os.path.dirname(__file__)
        logs_path = os.path.join(dir_name, "..", "logs", f"execution{eID}")
        local_files = get_local_files_r(logs_path)
    except:
        print(
            "Warning: unable to get UI files")

    request.session['remote_path'] = remote_path
    if execUpdate:
        request.session['results_dir'] = execUpdate.results_dir
    return render(request, 'pages/results.html',
                  {'executionsDone': execUpdate,
                   'remote_files': remote_files,
                   'local_files': local_files})


@login_required()
def upload_tool(request):
    if request.method == 'POST':
        document_form = DocumentForm(request.POST, request.FILES)
        if document_form.is_valid():
            uploaded_file = request.FILES['document']
            yaml_content = uploaded_file.read().decode('utf-8')
            try:
                tool = yaml_to_tool(yaml_content)
                request.session['tool_created'] = tool.name
                return redirect('tools')
            except ValueError as e:
                error_string = str(e)
                document_form = DocumentForm()
                return render(request, 'pages/upload_tool.html',
                              {'document_form': document_form, 'error': True,
                               'message': error_string})
        else:
            error_string = ""
            for field, errors in document_form.errors.items():
                error_string += f"{', '.join(errors)}\n"
            document_form = DocumentForm()
            return render(request, 'pages/upload_tool.html',
                          {'document_form': document_form, 'error': True,
                           'message': error_string})
    else:
        document_form = DocumentForm()

    return render(request, 'pages/upload_tool.html',
                  {'document_form': document_form})


@login_required()
def download_yaml(request, tool_name):
    """
    View to download a file containing the yaml of the tool_name tool.

    :param request: HTTP request
    :param tool_name: Name of the tool to be downloaded
    :return: HTTP Response with file download
    """
    try:
        tool_data = tool_to_yaml(tool_name)
        yaml_content = yaml.dump(tool_data, default_flow_style=False)
        response = HttpResponse(yaml_content,
                                content_type='application/x-yaml')
        response[
            'Content-Disposition'] = f'attachment; filename="{tool_name}.yaml"'
        return response
    except:
        raise Http404("Error downloading yaml file.")


@login_required
def download_remote_file(request, file_name):
    """
    Auxiliary view used for downloading a concrete file (a log/result file of
    an execution).

    :param request: request
    :param file_name: Name of the file involved
    :return: HTML render
    """
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
                response = HttpResponse(file_data,
                                        content_type='application/octet-stream')
                response[
                    'Content-Disposition'] = f'attachment; filename="{os.path.basename(file_path)}"'
                return response
        else:
            raise Http404("File not found.")
    except IOError:
        raise Http404("Error accessing the file.")
    finally:
        sftp.close()
        ssh.close()


@login_required
def download_remote_file(request, file_name):
    """
    Auxiliary view used for downloading a concrete file (a log/result file of
    an execution).

    :param request: request
    :param file_name: Name of the file involved
    :return: HTML render
    """
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
                response = HttpResponse(file_data,
                                        content_type='application/octet-stream')
                response[
                    'Content-Disposition'] = f'attachment; filename="{os.path.basename(file_path)}"'
                return response
        else:
            raise Http404("File not found.")
    except IOError:
        raise Http404("Error accessing the file.")
    finally:
        sftp.close()
        ssh.close()


@login_required
def download_local_file(request, path_to_file):
    """
    View to download a file from a local path or generate a YAML file from a dictionary.

    :param request: HTTP request
    :param local_path: Local path to file
    :return: HTTP Response with file download
    """
    if os.path.exists(path_to_file):
        with open(path_to_file, 'rb') as file_obj:
            response = HttpResponse(file_obj.read(),
                                    content_type='application/octet-stream')
            response[
                'Content-Disposition'] = f'attachment; filename="{os.path.basename(path_to_file)}"'
            return response
    else:
        raise Http404("File not found.")


@login_required
def create_tool(request):
    """
    Create tool view. There are mandatory fields to be filled (check
    pages/create_tool.html) needed for the execution and setup of a generic app.
    There are 2 types of fields (text/boolean) and different sections.

    :param request: request
    :return: HTML render
    """
    sections = ['application', 'setup', 'slurm', 'COMPSs', 'environment']

    if request.method == 'POST':
        form = CreateToolForm(request.POST)
        errors = {}

        if form.is_valid():
            tool_name = form.cleaned_data['name']
            use_args = request.POST.get('use_args') == 'on'
            if use_args == "true":
                use_args = True
            elif use_args == "false":
                use_args = False
            tool = Tool.objects.create(name=tool_name, use_args=use_args)
            field_names = set()

            custom_fields = {k: v for k, v in request.POST.items()
                             if k.startswith(f'custom_field_') or k.startswith(
                    'custom_boolean_field')}

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
                    preset_value = request.POST.get(f'preset_value_{index}',
                                                    None)
                    section = request.POST.get(f'section_{index}',
                                               None)
                if default_value == 'None' or default_value == '':
                    default_value = None
                if preset_value == 'None' or preset_value == '':
                    preset_value = None

                placeholder = request.POST.get(f'placeholder_{index}', None)

                tool.add_field(field_name, default_value=default_value,
                               preset_value=preset_value, section=section,
                               type=type, placeholder=placeholder)

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
                                  request.POST.get(
                                      'github_install_' + number) == 'on',
                                  request.POST.get(
                                      'github_install_dir_' + number),
                                  request.POST.get(
                                      'github_editable_' + number) == 'on',
                                  request.POST.get(
                                      'github_requirements_' + number) == 'on',
                                  request.POST.get(
                                      'github_target_' + number) == 'on')

            request.session['tool_created'] = tool.name
            return redirect('tools')

        else:
            return render(request, 'pages/create_tool.html',
                          {'form': form, 'errors': form.errors,
                           'sections': sections})
    else:
        form = CreateToolForm()

        return render(request, 'pages/create_tool.html',
                      {'form': form, 'sections': sections})


@login_required
def edit_tool(request, tool_name):
    """
        Edit tool view

        :param request: request
        :return: HTML render
        """
    tool = get_object_or_404(Tool, name=tool_name)
    errors = {}
    sections = ['application', 'setup', 'slurm', 'COMPSs', 'environment']

    if request.method == 'POST':

        form = CreateToolForm(request.POST, instance=tool)
        if form.is_valid():
            tool.name = form.cleaned_data['name']
            tool.use_args = request.POST.get('use_args') == 'on'
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

            additional_repos = {k: v for k, v in request.POST.items() if
                                k.startswith('github_repo_')}

            for repo_name, repo_value in additional_repos.items():
                if "branch" not in repo_name:
                    number = repo_name.split('github_repo_')[1]
                    branch = request.POST.get('github_branch_' + number)

                    tool.add_repo(repo_value,
                                  request.POST.get('github_branch_' + number),
                                  request.POST.get(
                                      'github_install_' + number) == 'on',
                                  request.POST.get(
                                      'github_install_dir_' + number),
                                  request.POST.get(
                                      'github_editable_' + number) == 'on',
                                  request.POST.get(
                                      'github_requirements_' + number) == 'on',
                                  request.POST.get(
                                      'github_target_' + number) == 'on')

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

        use_args = tool.use_args

        form = CreateToolForm(instance=tool, initial={
            'github_repo': first_repo,
            'github_branch': first_branch,
            'use_args': use_args
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
