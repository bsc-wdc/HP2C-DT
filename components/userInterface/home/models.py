import json

from django.db import models
from django.conf import settings


class Document(models.Model):
    document = models.FileField(upload_to='documents/')
    uploaded_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return str(self.document)


class Machine(models.Model):
    author = models.ForeignKey(settings.AUTH_USER_MODEL,
                               on_delete=models.CASCADE,
                               to_field='username',
                               null=True, blank=True)
    user = models.CharField(max_length=255, null=False)
    fqdn = models.CharField(max_length=255, null=False)


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
    show = models.BooleanField(default=True)
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
    show = models.BooleanField(default=True)
    type = models.CharField(max_length=100, default="")

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


class Key_Gen(models.Model):
    author = models.ForeignKey(settings.AUTH_USER_MODEL,
                               on_delete=models.CASCADE,
                               to_field='username',
                               null=True, blank=True)
    machine = models.ForeignKey("Machine",
                                on_delete=models.CASCADE,
                                to_field='id',
                                null=True, blank=True)
    public_key = models.CharField(max_length=3000, null=False)
    private_key = models.CharField(max_length=3000, null=False)

STATUS_CONN = [
    ('Active', 'Active'),
    ('Disconnect', 'Disconnect'),
    ('Timeout', 'Timeout')
]


class Tool(models.Model):
    name = models.CharField(max_length=100, unique=True)
    modules_list = models.TextField(blank=True, null=True)

    def get_modules_list(self):
        return self.modules_list.split(',') if self.modules_list else []

    def set_modules_list(self, modules_list):
        self.modules_list = ','.join(modules_list)

    def __str__(self):
        return self.name

    # Field-related methods
    def get_fields(self):
        return list(self.field_set.all())

    def add_field(self, field_name, default_value=None, preset_value=None,
                  section='application', type='text', placeholder=None):
        field = Field.objects.create(name=field_name, default_value=default_value,
                                     preset_value=preset_value, section=section,
                                     tool=self, type=type, placeholder=placeholder)
        return field

    def remove_field(self, field_name):
        Field.objects.filter(tool=self, name=field_name).delete()

    # Repo-related methods
    def get_repos(self):
        return list(self.repo_set.all())

    def add_repo(self, url, branch, install=False, install_dir=None, editable=False, requirements=False, target=False):
        repo = Repo.objects.create(url=url, branch=branch, install=install, install_dir=install_dir,
                                   editable=editable, requirements=requirements, target=target, tool=self)
        return repo

    def remove_repo(self, repo_url):
        Repo.objects.filter(tool=self, url=repo_url).delete()

    def remove_repos(self):
        Repo.objects.filter(tool=self).delete()

    def repos_json(self):
        repos = self.get_repos()
        repo_list = []

        for repo in repos:
            repo_data = {
                "url": repo.url,
                "branch": repo.branch,
                "install": repo.install,
                "install_dir": repo.install_dir,
                "editable": repo.editable,
                "requirements": repo.requirements,
                "target": repo.target
            }
            repo_list.append(repo_data)

        repos_json = json.dumps(repo_list)
        return repos_json


class Field(models.Model):
    name = models.CharField(max_length=100)
    default_value = models.CharField(max_length=255, null=True, blank=True, default=None)
    preset_value = models.CharField(max_length=255, null=True, blank=True, default=None)
    section = models.CharField(max_length=255, null=True, blank=True, default="application")
    tool = models.ForeignKey(Tool, on_delete=models.CASCADE)
    type = models.CharField(max_length=255, null=True, blank=True, default="text")
    placeholder = models.CharField(max_length=255, null=True, blank=True, default=None)

    def __str__(self):
        return self.name


class Repo(models.Model):
    url = models.URLField(max_length=255, null=True, blank=True, default=None)  # Changed to URLField
    branch = models.CharField(max_length=255, null=True, blank=True, default=None)
    install = models.BooleanField(default=False)
    install_dir = models.CharField(max_length=255, null=True, blank=True, default=None)
    editable = models.BooleanField(default=False)
    requirements = models.BooleanField(default=False)
    target = models.BooleanField(default=False)
    tool = models.ForeignKey(Tool, on_delete=models.CASCADE)

    def __str__(self):
        return f"{self.url} - {self.tool.name}"


class Connection(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL,
                             on_delete=models.CASCADE,
                             to_field='username',
                             null=True, blank=True)
    conn_id = models.AutoField(primary_key=True)
    status = models.CharField(max_length=30, choices=STATUS_CONN, default='Disconnect')
    machine = models.ForeignKey("Machine", on_delete=models.CASCADE,
                                blank=True, null=True, default=None)


class Execution(models.Model):
    eID = models.CharField(max_length=255, null=False)
    jobID = models.IntegerField(null=False)
    user = models.CharField(max_length=255, null=False)
    author = models.ForeignKey(settings.AUTH_USER_MODEL,
                               on_delete=models.CASCADE,
                               to_field='username',
                               null=True)
    nodes = models.IntegerField(null=False)
    status = models.CharField(max_length=255, null=False)
    time = models.CharField(max_length=255, null=False)
    execution_time = models.IntegerField(null=False)
    qos = models.CharField(max_length=255, null=False)
    checkpoint = models.IntegerField(null=False, default=0)
    checkpointBool = models.BooleanField(default=False)
    wdir = models.CharField(max_length=500, null=False)
    setup_path = models.CharField(max_length=500, null=False)
    autorestart = models.BooleanField(default=False)
    name_sim = models.CharField(max_length=255, null=False)
    machine = models.ForeignKey("Machine",
                                on_delete=models.CASCADE,
                                to_field='id',
                                null=True, blank=True)
    results_ftp_path = models.CharField(max_length=255, null=False)
    branch = models.CharField(max_length=255, null=False, default="main")
    g_bool = models.CharField(max_length=255, null=False, default="false")
    d_bool = models.CharField(max_length=255, null=False, default="false")
    t_bool = models.CharField(max_length=255, null=False, default="false")
    project_name = models.CharField(max_length=255, null=False, default="bsc19")
    tool = models.CharField(max_length=255, null=False, default="-")
    results_dir = models.CharField(max_length=500, default="")
    submit = models.DateTimeField(null=True, blank=True, default=None)
    start = models.DateTimeField(null=True, blank=True, default=None)
    end = models.DateTimeField(null=True, blank=True, default=None)

