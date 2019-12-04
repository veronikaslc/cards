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

const liveTableStyle = theme => ({
    tableHeader: {
        fontWeight: "300"
    },
    filterLabel: {
        margin: theme.spacing(0, 1, 0, 0)
    },
    filterContainer: {
        padding: theme.spacing(0, 1),
    },
    addFilterButton: {
        minWidth: 0,
        padding: 0,
        borderRadius: "50%",
        height: "24px",
        width: "24px",
        margin: theme.spacing(0.5, 0)
    },
    filterChips: {
        marginRight: theme.spacing(0.5),
        marginTop: theme.spacing(0.5),
        marginBottom: theme.spacing(0.5),
        '& >span': {
            margin: theme.spacing(0.5),
        },
    },
    saveButton: {
        position: 'absolute',
        right: theme.spacing(1)
    },
    dialogContent: {
        padding: theme.spacing(4,2,2,2),
        minHeight: theme.spacing(8)
    },
    answerField: {
        width: "100%",
    },
    categoryField: {
        width: "100%",
    },
    categoryOption: {
        whiteSpace: "normal",
    }
});

export default liveTableStyle;
