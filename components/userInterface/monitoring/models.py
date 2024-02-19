from django.db import models
from django.utils import timezone


class Edge(models.Model):
    name = models.CharField(max_length=100)
    last_update = models.DateTimeField(default=timezone.now)
    def __str__(self):
        return self.name


class Sensor(models.Model):
    name = models.CharField(max_length=100)
    edge = models.ForeignKey(Edge, on_delete=models.CASCADE)

    def __str__(self):
        return f"{self.name} - {self.edge}"


class SensorValue(models.Model):
    sensor = models.ForeignKey(Sensor, on_delete=models.CASCADE)
    value = models.FloatField()
    timestamp = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.sensor} - {self.value} at {self.timestamp}"


class SensorValueHistory(models.Model):
    sensor = models.ForeignKey(Sensor, on_delete=models.CASCADE)
    value = models.FloatField()
    timestamp = models.DateTimeField()

    def __str__(self):
        return f"{self.sensor} - {self.value} at {self.timestamp}"