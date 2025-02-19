def add_actuation(actuations, edge_name, device_name, values):
    """
    Add a new actuation to the actuations dictionary for the given edge_name,
    device_name, and values.
    """
    if edge_name not in actuations:
        actuations[edge_name] = {}

    actuations[edge_name][device_name] = values
    return actuations