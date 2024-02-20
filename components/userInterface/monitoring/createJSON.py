import os
import json

# Directorio donde se encuentran los deployments
deployments_dir = 'deployments'

# Directorio donde se guardarán los archivos JSON resultantes
output_dir = 'output_json'


# Función para crear el JSON de un dispositivo
def create_device_json(device):
    device_json = {
        "model": "monitoring.device",
        "pk": None,
        # Puedes dejarlo como None y Django lo generará automáticamente
        "fields": {
            "name": device["label"],
            "edge": None,
            # Necesitarás establecer esto después de cargar los deployments en Django
            "panel_link": "http://localhost:3000/d-solo/bb7d6150-0f89-471f-9589-129837044889/hp2c-dt?orgId=1&theme=light&panelId=1"
        }
    }
    return device_json


# Función para crear el JSON de un edge
def create_edge_json(edge_file):
    with open(edge_file, 'r') as f:
        edge_data = json.load(f)

    edge_name = edge_data["global-properties"]["label"]
    devices = edge_data["devices"]

    edge_json = {
        "model": "monitoring.edge",
        "pk": None,
        # Puedes dejarlo como None y Django lo generará automáticamente
        "fields": {
            "name": edge_name,
            "deployment": None
            # Necesitarás establecer esto después de cargar los deployments en Django
        }
    }

    device_jsons = [create_device_json(device) for device in devices]

    return edge_json, device_jsons


# Iterar sobre los deployments
for deployment_name in os.listdir(deployments_dir):
    deployment_dir = os.path.join(deployments_dir, deployment_name)

    # Iterar sobre los archivos de edge en el deployment
    for edge_file_name in os.listdir(deployment_dir):
        if edge_file_name.endswith('.json'):
            edge_file_path = os.path.join(deployment_dir, edge_file_name)
            edge_json, device_jsons = create_edge_json(edge_file_path)

            # Guardar el JSON de edge
            with open(os.path.join(output_dir, edge_file_name), 'w') as f:
                json.dump(edge_json, f, indent=4)

            # Guardar los JSONs de los dispositivos asociados al edge
            for device_json in device_jsons:
                device_name = device_json["fields"]["name"]
                with open(os.path.join(output_dir, f'{device_name}.json'),
                          'w') as f:
                    json.dump(device_json, f, indent=4)
