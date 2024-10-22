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
  path('ssh_keys/', views.ssh_keys, name='ssh_keys'),
  path('ssh_keys_generation/', views.ssh_keys_generation, name='ssh_keys_generation'),
  path('tools/', views.tools, name='tools'),
  path('hpc_machines/', views.hpc_machines, name='hpc_machines'),
  path('results/', views.results, name='results'),
  path('download/<str:file_name>/', views.download_remote_file, name='download_remote_file'),
  path('download_local/<str:path_to_file>/', views.download_local_file, name='download_local_file'),
  path('download_yaml/<str:tool_name>/', views.download_yaml, name='download_yaml'),
  path('create_tool/', views.create_tool, name='create_tool'),
  path('edit_tool/<str:tool_name>/', views.edit_tool, name='edit_tool'),
]
