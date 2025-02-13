def add_actuation(actuations, edge_name, device_name, values):
    if edge_name not in actuations:
        actuations[edge_name] = {}

    actuations[edge_name][device_name] = values
    return actuations