from django.urls import path
from . import views

app_name = "monitoring"
urlpatterns = [
    path("edges/", views.edge_list, name="edge_list"),
    path("sensors/<int:edge_id>/", views.sensor_list, name="sensor_list"),
    path("sensor_values/<int:sensor_id>/", views.sensor_values, name="sensor_values"),
]
