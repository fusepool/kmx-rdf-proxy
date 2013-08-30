import json
import requests
import logging



__author__ = 'daniel'

HOST = "beta.fusepool.com"
HOST = "localhost:8080"
def main():
    searchterm = "apparatus"
    data = {
        "contentStoreUri": "http://"+HOST+"/ecs/",
        "contentStoreViewUri": "http://"+HOST+"/ecs/?search="+searchterm,
#        "subjects": [],
        "searchs": [searchterm],
        "labels": {
            "<http://localhost:8080/ecs/content/fa038b12621a477f893a55a04a01b7fd>":
                "Positive",
            "<http://localhost:8080/ecs/content/66747a4e2117c3949ee0e70fc1e59ff2>":
                "Positive",
            "<http://localhost:8080/ecs/content/7cd4ec9f87fc92c8c408ab0b4d37d101>":
                "Positive",
            "<http://localhost:8080/ecs/content/1822f5935be10b09358d346f45a6c439>":
                "Negative",
            "<http://localhost:8080/ecs/content/5a7d517033c65e515543dc4f27c88b00>":
                "Negative",
            "<http://localhost:8080/ecs/content/10925d052659761e61471fd7ca6f605a>":
                "Negative",
        },
            
#        "items": 500,
#        "offset": 0,
#        "maxFacets": 10,
    }
    header = {"Accipt": "application/rdf+xml"}
    res = requests.post("http://"+HOST+"/kmxrdfproxy/ranking/?header_Accept=application%2Frdf%2Bxml",
                        data=json.dumps(data),
                        headers=header)
    print "result:", res.content

if __name__ == '__main__':
    logging.basicConfig(level=logging.DEBUG)
    main()
