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
    wdir = models.CharField(max_length=2048, null=False)
    installDir= models.CharField(max_length=2048, null=False)
    dataDir=models.CharField(max_length=2048, null=False)


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


class Connection(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL,
                             on_delete=models.CASCADE,
                             to_field='username',
                             null=True, blank=True)
    conn_id = models.AutoField(primary_key=True)
    status = models.CharField(max_length=30, choices=STATUS_CONN, default='Disconnect')


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

