//
//  Licensed to the Apache Software Foundation (ASF) under one
//  or more contributor license agreements.  See the NOTICE file
//  distributed with this work for additional information
//  regarding copyright ownership.  The ASF licenses this file
//  to you under the Apache License, Version 2.0 (the
//  "License"); you may not use this file except in compliance
//  with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.
//
// Locate all visible lfs:dataEntry nodes
use(function(){
    var path = request.getRequestParameter("path");
    if (!path) {
        return {'uixp': ''};
    }
    path = path.getString();
    var query = resolver.findResources("select * from [lfs:ExtensionPoint] as n WHERE n.'lfs:extensionPointId' = '"+path+"'`", "JCR-SQL2");
  
    // Dump our iterator into a list of strings
    var nodePaths = [];
    while (query.hasNext()) {
      var resource = query.next();
      nodePaths.push(resource.getPath());
    }
  
    return {
      uixp: nodePaths
    };
  });