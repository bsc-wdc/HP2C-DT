"""
This module contains the functions related to the use of tools in the
application. These methods generate forms and dictionaries to store the
tool data.
"""

import yaml
from django import forms

from home.models import Tool


def auto_convert(value):
    """
    Convert string values to the appropriate type (list, int, float, bool...)

    :param value: Value involved
    :return: If possible, converted value; otherwise, returns None for "None"
    """
    if value == "None":
        return None

    try:
        return yaml.safe_load(value)
    except Exception:
        return value


def extract_tool_data(request, tool_name):
    """
    Parse execution form (of a custom tool) and returns a dictionary storing
    all the information.

    :param request: request
    :param tool_name: Involved tool name
    :return: Dictionary with tool info
    """
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
                    value = request.POST.get(f'preset_value_boolean_id_{field_form}', None)
                    tool_data[section][field_model.name] = auto_convert(value)
                else:
                    value = request.POST.get(field_form, False)
                    tool_data[section][field_model.name] = auto_convert(value)
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
                    value = request.POST.get(f'preset_value_id_{field_form}', None)
                    tool_data[section][field_model.name] = auto_convert(value)
                else:
                    value = request.POST.get(field_form, None)
                    tool_data[section][field_model.name] = auto_convert(value)

    return tool_data


def tool_to_yaml(tool_name):
    tool = Tool.objects.get(name=tool_name)
    tool_data = {}
    tool_data['tool_name'] = tool_name
    tool_data['repos'] = tool.repos_json()
    tool_data['modules_list'] = tool.get_modules_list()
    tool_data['fields'] = []

    for field in tool.get_fields():
        tool_data['fields'].append({
            'name': field.name,
            'default_value': field.default_value if field.default_value else "None",
            'preset_value': field.preset_value if field.preset_value else "None",
            'section': field.section,
            'type': field.type,
            'placeholder': field.placeholder if field.placeholder else "None"
        })

    return tool_data


def yaml_to_tool(yaml_content):
    tool_data = yaml.safe_load(yaml_content)
    tool_name = tool_data['tool_name']

    if Tool.objects.filter(name=tool_name).exists():
        raise ValueError(f"A tool with name '{tool_name}' already exists.")

    tool = Tool.objects.create(name=tool_name)
    tool.set_modules_list(tool_data['modules_list'])

    for field_data in tool_data['fields']:
        tool.add_field(
            field_name=auto_convert(field_data['name']),
            default_value=auto_convert(field_data.get('default_value')),
            preset_value=auto_convert(field_data.get('preset_value')),
            section=auto_convert(field_data.get('section')),
            type=auto_convert(field_data.get('type')),
            placeholder=auto_convert(field_data.get('placeholder'))
        )

    for repo_data in yaml.safe_load(tool_data['repos']):
        tool.add_repo(
            url=auto_convert(repo_data['url']),
            branch=auto_convert(repo_data['branch']),
            install=auto_convert(repo_data['install']),
            install_dir=auto_convert(repo_data.get('install_dir')),
            editable=auto_convert(repo_data['editable']),
            requirements=auto_convert(repo_data['requirements']),
            target=auto_convert(repo_data['target'])
        )

    return tool




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
            if initial == "True":
                initial = True
            elif initial == "False":
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


def get_environment_variables(setup):
    """
    Get environment variables from setup-environment section in the setup
    dictionary.

    :param setup: Dictionary containing the setup info
    :return: Dictionary with pairs environment variable - value
    """
    exported_variables = {}

    if 'environment' in setup and isinstance(setup['environment'], dict):
        for key, value in setup['environment'].items():
            exported_variables[key] = value
    return exported_variables
