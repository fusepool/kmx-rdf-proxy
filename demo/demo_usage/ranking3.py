import json
import requests
import logging



__author__ = 'daniel'

HOST = "beta.fusepool.com"
#HOST = "localhost:8080"
def main():
    searchterm = "apparatus"
    data = {
        "contentStoreUri": "http://"+HOST+"/ecs/",
        "contentStoreViewUri": "http://"+HOST+"/ecs/?search="+searchterm,
#        "subjects": [],
        "searchs": [searchterm],
        "labels": {
#            "http://localhost:8080/ecs/content/fa038b12621a477f893a55a04a01b7fd":
#                "Positive",
#            "http://localhost:8080/ecs/content/66747a4e2117c3949ee0e70fc1e59ff2":
#                "Positive",
#            "http://localhost:8080/ecs/content/7cd4ec9f87fc92c8c408ab0b4d37d101":
#                "Positive",
#            "http://localhost:8080/ecs/content/1822f5935be10b09358d346f45a6c439":
#                "Negative",
#            "http://localhost:8080/ecs/content/5a7d517033c65e515543dc4f27c88b00":
#                "Negative",
#            "http://localhost:8080/ecs/content/10925d052659761e61471fd7ca6f605a":
#                "Negative",
#             "http://fusepool.info/doc/patent/EP-1001341-A1":
#                "Negative",
#             "http://fusepool.info/doc/patent/EP-1005047-A2":
#                "Positive",
#             "http://fusepool.info/id/3ec43ecf-4b8f-45d7-8e21-302701c6c0f2":
#                "Positive",
#             "http://fusepool.info/doc/patent/EP-1004526-A2":
#                "Negative",
#            "http://fusepool.info/doc/patent/EP-1004530-A2": "Positive",
#            "http://fusepool.info/id/c0e29ee8-f991-4fde-b90d-196259bba241":
#                "Negative",
#            "http://fusepool.info/doc/patent/EP-1004570-A1": "Negative",
#            "http://fusepool.info/id/8bf915a2-beb0-4805-a45f-f970e14d4d89":
#                "Negative",
#            "http://fusepool.info/doc/patent/EP-1004526-A2": "Negative",
#            "http://fusepool.info/id/ed3b0ada-8b33-496a-b516-98098588dcc3":
#                "Negative",
#            "http://fusepool.info/doc/patent/EP-1004545-A1": "Positive",

        },
            
#        "items": 500,
#        "offset": 0,
#        "maxFacets": 10,
    }
    data = '''{
    "contentStoreUri":"/ecs/",
    "contentStoreViewUri":"/ecs/",
    "searchs":["method"],
    "type":[],
    "subject":[],
    "labels":{
        "http://fusepool.info/doc/patent/EP-1010724-A1":"Positive",
        "http://fusepool.info/doc/patent/EP-1010420-A1":"Positive",
        "http://fusepool.info/doc/patent/EP-1018334-A1":"Negative",
        "http://fusepool.info/doc/patent/EP-1000607-A1":"Negative",
        "http://fusepool.info/doc/patent/EP-1010416-A1":"Negative"}
    }
'''
    print json.loads(data)
    data = json.loads(data)
    params = {"header_Accept": "application/rdf+xml",
              "json": json.dumps(data)}
    #header = {"Accept": "application/rdf+xml"}
    header = {"Accept": "text/turtle"}
    print len(params['json'])
    req = requests.post("http://"+HOST+"/kmxrdfproxy/ranking",
                        data=json.dumps(data),
                        headers=header
                        )
    print req.url
    print "result:", req.content

if __name__ == '__main__':
    logging.basicConfig(level=logging.DEBUG)
    main()

