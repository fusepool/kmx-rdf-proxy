import json
import requests

__author__ = 'daniel'


def main():
    data = {
        "contentStoreUri": "http://example.com/",
        "contentStoreViewUri": "http://example.com/",
        "subjects": [""],
        "searchs": ["a", "b"],
        "items": 1,
        "offset": 0,
        "maxFacets": 1,
    }
    res = requests.post("http://localhost:8080/kmxrdfproxy/ranking/",
                        data=json.dumps(data))
    print "result:", res.content

if __name__ == '__main__':
    main()