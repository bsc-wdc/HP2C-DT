from django.db.models.signals import pre_save, post_migrate
from django.contrib.auth.signals import user_logged_out
from django.dispatch import receiver
from .models import Connection

@receiver(post_migrate)
def clear_connections(sender, **kwargs):
    Connection.objects.all().delete()

@receiver(user_logged_out)
def on_user_logout(sender, request, user, **kwargs):
    Connection.objects.filter(user=user, status='Active').update(status='Disconnected')
