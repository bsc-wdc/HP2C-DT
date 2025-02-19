from funcs.utils import add_actuation


def main(sensors, actuators, threshold):
    """
    The method checks whether the written voltage is lower than threshold
    """
    print(f"[Voltlimitation] Using {threshold} as threshold")
    actuate = False
    actuations = {}
    for edge_name, devices in sensors.items():
        for device_name, data in devices.items():
            if data["class-name"] == "OpalVoltmeter":
                last_measurement = data["measurements"][len(data["measurements"])-1]
                if last_measurement["value"][0] > int(threshold):
                    actuate = True
                    break

    if actuate:
        result = "Voltage Limit exceeded, turning off actuators: "
        for edge_name, devices in actuators.items():
            for device_name, data in devices.items():
                if data["class-name"] == "OpalSwitch":
                    size = int(data["size"])
                    values = ["OFF"] * size
                    actuations = add_actuation(actuations=actuations,
                                               edge_name=edge_name,
                                               device_name=device_name,
                                               values=values)
                    result += f"{edge_name}-{device_name} "

    else:
        result = "No voltmeter's measurements exceeded the threshold"
    return result, actuations
