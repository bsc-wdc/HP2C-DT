from django.dispatch import receiver
from django.apps import apps
from .services import start_influx_query


def start_influx_comms(**kwargs):
    if apps.get_app_config('monitoring').ready:
        start_influx_query()

