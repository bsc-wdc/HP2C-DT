from django.urls import path

from . import views

urlpatterns = [
  path('dashboard', views.index, name='dashboard'),
  path('<str:edge_name>/<str:device_name>', views.device_detail, name='device_detail'),
  path('<str:edge_name>', views.edge_detail, name='edge_detail'),
  path('', views.login_page, name='login'),
  path('register/', views.register_page, name='register'),
  path('logout/', views.logoutUser, name='logout'),
  path('new_machine/', views.new_machine, name='new_machine'),
  path('machines/', views.machines, name='machines')
]
