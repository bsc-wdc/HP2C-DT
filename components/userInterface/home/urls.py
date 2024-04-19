from django.urls import path
from django.contrib.auth import views as auth_views

from . import views

urlpatterns = [
  path(''       , views.index, name='index'),
  path('tables/', views.tables, name='tables'),
  path('<str:edge_name>/<str:device_name>', views.device_detail, name='device_detail'),
]
