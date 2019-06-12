import json

def parse_bed(json_input):
    """ Converts a bed JSON string to a GA4GHFeature JSON String
    
    Args:
        :param str: JSON string containing bed data
    """
    #vars
    first_start, first_end, first_chr = "", "", ""
    s_dict, e_dict, chr_dict = {}, {}, {}
    colon_index, parens_index = 0, 0
    json_output = ""

    #clean string
    json_input = json_input.replace(" ","")
    json_input = json_input[1:]
    
    #loading reference chromosome

    #first chromosome
    colon_index = json_input.find(":")
    first_chr= json_input[:colon_index].replace("""\"""","")
    json_input = json_input[colon_index + 1:]
    
    #load all but first ref chromosome into dictionary
    parens_index  = json_input.find("}")
    chr_dict = json.loads(json_input[:parens_index + 1])
    json_input = json_input[parens_index + 2:]
    
    #add first start value in range 
    colon_index = json_input.find(":")
    first_start = json_input[:colon_index].replace("""\"""","")
    json_input = json_input[colon_index + 1:]
    
    #load rest into dictionary
    parens_index = json_input.find("}")
    s_dict = json.loads(json_input[:parens_index + 1])
    json_input = json_input[parens_index + 2:]
    
    #add first end range value 
    colon_index = json_input.find(":")
    first_end = json_input[:colon_index].replace("""\"""","")
    json_input = json_input[colon_index + 1:]
    
    #load rest into dictionary
    parens_index = json_input.find("}")
    e_dict = json.loads(json_input[:parens_index + 1])
    json_input = json_input[parens_index + 2:]
    
    return build_json_from_bed(first_start, first_end, first_chr, s_dict, e_dict, chr_dict)
    
def build_json_from_bed(first_start, first_end, first_chr, s_dict, e_dict, chr_dict):
    """ dConverts a parsed bed file into a json string in GA4GH schema.
        
    Args: 
        :param str: first start range
        :param str: first end range
        :param str: first reference chromosome
        :param dict: start range values
        :param dict: dictionary of 
    
    """
    bed_content = "\"referenceName\":{}, \"start\":{}, \"end\":{}".format("\""+first_chr+"\"", "\""+str(first_start)+"\"", "\""+str(first_end)+"\"")
    json_ga4gh = "{\"features\":["
    for i in range(len(chr_dict) + 1):
        if i < len(chr_dict):
            json_ga4gh = json_ga4gh + "{" + bed_content + "},"
            bed_content = "\"referenceName\":{}, \"start\":{}, \"end\":{}".format("\""+chr_dict[str(i)]+"\"", "\""+str(s_dict[str(i)])+"\"", "\""+str(e_dict[str(i)])+"\"")
        else:
            json_ga4gh = json_ga4gh + "{" + bed_content + "}"
            
    
    #ending json
    json_ga4gh = json_ga4gh + "]}"
    return json_ga4gh
        
    
    
    