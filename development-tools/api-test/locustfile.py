from locust import HttpUser, task, between, TaskSet, SequentialTaskSet

class MyTaskSet(SequentialTaskSet):

    headers = {
        'Authorization': 'Basic a2FyYWY6a2FyYWY=',
        'Content-Type': 'application/json'
    }

    def on_start(self):
        # Initialize or setup any data before starting the tasks
        self.network_id = "spain-network"
        self.create_network()

    @task
    def create_network(self):
        self.client.post(
            "/onos/vnets/api",
            headers=self.headers,
            json={"networkId": self.network_id}
        )

    @task
    def get_network(self):
        self.client.get(f"/onos/vnets/api/{self.network_id}", headers=self.headers)

    @task
    def get_status(self):
        self.client.get("/onos/vnets/api/status", headers=self.headers)

    @task
    def add_port(self):
        # Retrieve device IDs
        print("retrieving ids")
        device_response = self.client.get("/onos/v1/devices", headers={'Authorization': 'Basic a2FyYWY6a2FyYWY='})
        print(device_response)
        if device_response.status_code == 200:
            print("got it ")
            devices = device_response.json()
            if devices and 'devices' in devices:
                for device in devices['devices']:
                    device_id = device['id']
                    port_payload = {
                        "networkId": self.network_id,
                        "networkEndpoints": [f"{device_id}/1"]
                    }
                    self.client.post(
                        "/onos/vnets/api/port",
                        headers=self.headers,
                        json=port_payload
                    )

    @task
    def delete_network(self):
        self.client.delete(f"/onos/vnets/api/{self.network_id}", headers=self.headers)

    def on_stop(self):
        # Cleanup actions when the test is stopped
        self.delete_network()


class MyUser(HttpUser):
    wait_time = between(1, 5)  # Random wait time between requests (in seconds)
    tasks = [MyTaskSet]
