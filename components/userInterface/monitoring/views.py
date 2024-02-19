from django.shortcuts import render
from .models import Edge, Sensor, SensorValue, SensorValueHistory
import random


def edge_list(request):
    edges = Edge.objects.all()
    for edge in edges:
        print(edge.name)
    return render(request, "monitoring/edge_list.html", {"edges": edges})


def sensor_list(request, edge_id):
    edge = Edge.objects.get(id=edge_id)
    sensors = Sensor.objects.filter(edge=edge)
    return render(
        request, "monitoring/sensor_list.html", {"edge": edge, "sensors": sensors}
    )


def sensor_values(request, sensor_id):
    sensor = Sensor.objects.get(id=sensor_id)
    values = SensorValueHistory.objects.filter(sensor=sensor)
    return render(
        request, "monitoring/sensor_values.html", {"sensor": sensor, "values": values}
    )
