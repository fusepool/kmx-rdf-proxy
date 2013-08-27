import json
import requests

__author__ = 'daniel'


def main():
    data = {
        "contentStoreUri": "http://localhost:8080/ecs/",
        "contentStoreViewUri": "http://localhost:8080/ecs/",
        "subjects": [],
        "searchs": ["shampoo"],
        "items": 500,
        "offset": 0,
        "maxFacets": 10,
    }
    auth = ('admin', 'admin')
    res = requests.post("http://localhost:8080/kmxrdfproxy/ranking/",
                        data=json.dumps(data), auth=auth)
    print "result:", res.content

if __name__ == '__main__':
    main()
