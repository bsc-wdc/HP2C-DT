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
  path('machines/', views.machines, name='machines'),
  path('ssh_keys/', views.ssh_keys, name='ssh_keys'),
  path('ssh_keys_generation/', views.ssh_keys_generation, name='ssh_keys_generation'),
  path('tools/', views.tools, name='tools'),
  path('hpc_machines/', views.hpc_machines, name='hpc_machines'),
  path('run_sim/', views.run_sim, name='run_sim'),
  path('executions/', views.executions, name='executions')
]
