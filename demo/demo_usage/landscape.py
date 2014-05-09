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
        }
    header = {"Accept": "application/rdf+xml"}
    res = requests.post("http://"+HOST+"/kmxrdfproxy/landscape/?header_Accept=application%2Frdf%2Bxml",
                        data=json.dumps(data),
                        headers=header)
    print "result:", res.content

if __name__ == '__main__':
    logging.basicConfig(level=logging.DEBUG)
    main()
