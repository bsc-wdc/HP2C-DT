from django.db import models


class Deployment(models.Model):
    name = models.CharField(max_length=100)
    def __str__(self):
        return self.name

class Edge(models.Model):
    name = models.CharField(max_length=100)
    deployment = models.ForeignKey(Deployment, on_delete=models.CASCADE)
    def __str__(self):
        return self.name


class Device(models.Model):
    name = models.CharField(max_length=100)
    edge = models.ForeignKey(Edge, on_delete=models.CASCADE)
    panel_link = models.CharField(max_length=100)
    def __str__(self):
        return f"{self.name} - {self.edge}"

