import threading
import time
from datetime import datetime, timedelta, timezone
from influxdb import InfluxDBClient
from .models import Edge, Sensor, SensorValue, SensorValueHistory


def influx_query():
    # Configure InfluxDB client
    client = InfluxDBClient(host='0.0.0.0', port=8086, database='hp2cdt')

    # Execute query periodically
    while True:
        try:
            measurements = client.query('SHOW MEASUREMENTS')
            print(measurements)
            for measurement in measurements.get_points():
                m = measurement['name']
                edge, created = Edge.objects.get_or_create(
                    name=m,
                )
                formatted_time = int(edge.last_update.timestamp() * 1000)
                result = client.query(
                    f'SELECT * FROM {m} WHERE time > {formatted_time}'
                )
                print(len(result))

                for point in result.get_points():
                    lu = datetime.strptime(point['time'], '%Y-%m-%dT%H:%M:%SZ')
                    lu = lu.replace(tzinfo=timezone.utc)
                    sensor, created = Sensor.objects.get_or_create(
                        name=point['device'],
                        edge=edge
                    )
                    sensor_value, created = SensorValue.objects.update_or_create(
                        sensor=sensor,
                        value=point['value'],
                        timestamp=lu
                    )
                    _ = SensorValueHistory.objects.create(
                        sensor=sensor,
                        value=sensor_value.value,
                        timestamp=lu
                    )
                    edge.last_update = lu
            print("PRINTING VALUES")
            edges = Edge.objects.all()
            for e in edges:
                print(e.name)
                sensors = Sensor.objects.filter(edge=e)
                for s in sensors:
                    print("    ", s.name)
                    values = SensorValueHistory.objects.filter(sensor=s)
                    for v in values:
                        print("        ", v.value)
            time.sleep(5)
        except Exception as e:
            print(f"Error while consulting database: {e}")


# Start influx_query in a separate thread
def start_influx_query():
    thread = threading.Thread(target=influx_query)
    thread.daemon = True
    thread.start()
    print("finish")
