from django.urls import path
from . import views

app_name = "monitoring"
urlpatterns = [
    path("deployments/", views.deployment_list, name="deployment_list"),

    path("deployments/<str:deployment_name>/", views.edge_list,
         name="edge_list"),

    path("deployments/<str:deployment_name>/<str:edge_name>/",
         views.device_list, name="device_list"),

    path(
        "deployments/<str:deployment_name>/<str:edge_name>/<str:device_name>/",
        views.display_panel, name="display_panel"),
]
