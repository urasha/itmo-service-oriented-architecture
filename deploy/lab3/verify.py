"""Exercise lab 2 contracts and lab 3 routing against a running local stack."""
import http.client
import json
import os
import ssl
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import urlencode
from uuid import uuid4

ENV = os.environ
runtime = Path(ENV["SOA_RUNTIME"])
flats = (int(ENV["FLATS_HTTPS_PORT"]), ssl.create_default_context(cafile=runtime / "jetty-base/etc/flats.crt"))
agency = (int(ENV["AGENCY_HTTPS_PORT"]), ssl.create_default_context(cafile=runtime / "agency-tls/agency.crt"))


def request(service, method, path, status, **query):
    port, context = service
    connection = http.client.HTTPSConnection("localhost", port, context=context, timeout=10)
    try:
        url = path + ("?" + urlencode(query, doseq=True) if query else "")
        connection.request(method, url, headers={"Origin": ENV["AGENCY_ALLOWED_ORIGIN"]})
        response = connection.getresponse()
        body = response.read()
        if response.status != status:
            raise AssertionError(f"{method} {path}: expected {status}, got {response.status} {body!r}")
        data = json.loads(body) if body and "application/json" in response.getheader("Content-Type", "") else body
        return response.headers, data
    finally:
        connection.close()


def verify():
    sample = {"name": "ЛР3-" + uuid4().hex[:12], "coordinates": {"x": 1, "y": 2}, "area": 54,
              "price": 10.25, "numberOfRooms": 2, "new": False, "view": "YARD",
              "transport": None, "house": {"name": "Дом", "year": 2000, "numberOfFloors": 5,
              "numberOfFlatsOnFloor": 3, "numberOfLifts": None}}
    ids = []
    try:
        for price in (10.25, 19.75, 5.99):
            headers, _ = request(flats, "POST", "/flats", 201, flat=json.dumps(dict(sample, price=price)))
            ids.append(int(headers["Location"].split("/")[-1]))
        first, second, third = ids
        flat_path = f"/flats/{first}"
        _, flat = request(flats, "GET", flat_path, 200)
        assert flat["price"] == 10.25
        query = {"sort": ["price:desc", "id:asc"], "filter": json.dumps({
            "name": sample["name"], "house": {"numberOfFloors": 5}})}
        headers, page = request(flats, "GET", "/flats", 200, page=1, size=2, **query)
        assert [row["id"] for row in page] == [second, first]
        assert headers["X-Total-Count"] == "3"
        _, page = request(flats, "GET", "/flats", 200, page=2, size=2, **query)
        assert [row["id"] for row in page] == [third]
        for path in ("min-creation-date", "max-new"):
            request(flats, "GET", "/flats/" + path, 200)
        _, count = request(flats, "GET", "/flats/count-by-transport", 200, transport="null")
        assert count["count"] >= 3
        request(flats, "PUT", flat_path, 200, flat=json.dumps(sample))
        changes = ({"area": 65}, {"house": {"numberOfFloors": 7}})
        with ThreadPoolExecutor(2) as workers:
            list(workers.map(lambda patch: request(flats, "PATCH", flat_path, 200, flat=json.dumps(patch)), changes))
        _, flat = request(flats, "GET", flat_path, 200)
        assert flat["area"] == 65 and flat["house"]["numberOfFloors"] == 7

        request(flats, "PATCH", flat_path, 422, flat='{"numberOfRooms":0}')
        request(flats, "GET", "/flats", 400, sort="missing:asc")
        cheapest = f"/agency/get-cheapest/{first}/{second}"
        instances = set()
        for _ in range(6):
            headers, result = request(agency, "GET", cheapest, 200)
            assert result["id"] == first
            assert headers["Access-Control-Allow-Origin"] == ENV["AGENCY_ALLOWED_ORIGIN"]
            instances.add(headers["X-Agency-Instance"])
        assert instances == {"agency-1", "agency-2"}, instances
        _, result = request(agency, "GET", f"/agency/get-most-expensive/{third}/{second}/{first}", 200)
        assert result["id"] == second
        headers, _ = request(agency, "GET", f"/agency/get-cheapest/{first}/{first}", 303)
        assert headers["Location"] == ENV["FLATS_PUBLIC_BASE_URL"].rstrip("/") + flat_path
        request(agency, "GET", "/agency/get-cheapest/x/1", 400)
        request(flats, "DELETE", f"/flats/{third}", 204)
        ids.remove(third)
        _, error = request(flats, "GET", f"/flats/{third}", 404)
        assert {"timestamp", "message"} <= error.keys()
        request(agency, "GET", f"/agency/get-cheapest/{third}/{first}", 404)
        for path in ("/app/index.html", "/app/config.js", "/docs/index.html", "/docs/flats.yaml", "/docs/agency.yaml"):
            request(flats, "GET", path, 200)
        print("OK: CRUD, concurrent PATCH, queries, statistics, errors, redirects, both HAProxy backends and client files")
    finally:
        for flat_id in ids:
            request(flats, "DELETE", f"/flats/{flat_id}", 204)


if __name__ == "__main__":
    verify()
