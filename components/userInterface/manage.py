#!/usr/bin/env python
"""Django's command-line utility for administrative tasks."""
import os
import sys

from django.core.signals import request_started
from django.dispatch import receiver


def main():
    """Run administrative tasks."""
    os.environ.setdefault("DJANGO_SETTINGS_MODULE",
                          "userInterface.settings")
    try:
        from django.core.management import execute_from_command_line
    except ImportError as exc:
        raise ImportError(
            "Couldn't import Django. Are you sure it's installed and "
            "available on your PYTHONPATH environment variable? Did you "
            "forget to activate a virtual environment?"
        ) from exc

    execute_from_command_line(sys.argv)

"""
@receiver(request_started)
def on_request_started(sender, **kwargs):
    if 'runserver' in sys.argv:
        from monitoring.services import start_influx_query
        start_influx_query()
"""

if __name__ == "__main__":
    main()
