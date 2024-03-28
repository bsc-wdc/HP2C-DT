from django.db import models

# Create your models here.

class Product(models.Model):
    id    = models.AutoField(primary_key=True)
    name  = models.CharField(max_length = 100) 
    info  = models.CharField(max_length = 100, default = '')
    price = models.IntegerField(blank=True, null=True)

    def __str__(self):
        return self.name

class Deployment(models.Model):
    name = models.CharField(max_length=100)
    uid = models.CharField(max_length=100, default="")
    dashboard_name = models.CharField(max_length=100, default="")
    server_url = models.CharField(max_length=100, default="http://localhost:8080")
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
    timeseries_link = models.CharField(max_length=100, default="")
    table_link = models.CharField(max_length=100, default="")
    is_actionable = models.BooleanField(default=False)
    is_categorical = models.BooleanField(default=None, null=True)
    categories_field = models.TextField(default=None, blank=True, null=True)
    size = models.IntegerField(default=None, null=True)

    def get_categories(self):
        if self.categories_field:
            return self.categories_field.split(',')
        else:
            return []

    def set_categories(self, value):
        self.categories_field = ','.join(value)

    categories = property(get_categories, set_categories)

    def __str__(self):
        return f"{self.name} - {self.edge}"

