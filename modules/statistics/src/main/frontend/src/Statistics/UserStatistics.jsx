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
import React, { useState } from "react";
import {
  Card,
  CardContent,
  Grid, 
  withStyles, 
  Typography 
} from "@material-ui/core";
import statisticsStyle from "./statisticsStyle.jsx";

function UserStatistics(props) {
  const { classes } = props;
  let [ currentStatistic, setCurrentStatistic ] = useState([]);
  let [initialized, setInitialized] = useState(false);
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();

  // Obtain information about the Statistics available to the user
  let initialize = () => {
    setInitialized(true);

    // Fetch the statistics
    fetch("/query?query=select * from [lfs:Statistic]")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((response) => {
        if (response.totalrows == 0) {
          setError("No statistics have been added yet.");
        }
        fetchAll(response["rows"]);
      })
      .catch(handleError);
  }

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response.statusText ? response.statusText : response.toString());
  };

  // If no forms can be obtained, we do not want to keep on re-obtaining statistics
  if (!initialized) {
    initialize();
  }

  // If an error was returned, report the error
  if (error) {
    return (
      <Card>
        <CardContent>
          <Typography>{error}</Typography>
        </CardContent>
      </Card>
    );
  }

  let fetchStat = (stat) => {
    // TODO: change servlet to take statistic JSON as request body to servlet

    // fetch(`/Statistics/${stat['@name']}.simple.json`)
    //   .then((response) => response.ok ? response.json() : Promise.reject(response))
    //   .then((simpleJson) => {
    //     let requestData = {
    //       'name': simpleJson.name,
    //       'x-label': simpleJson.xVar['jcr:uuid'],
    //       'y-label': simpleJson.yVar['jcr:uuid']
    //       // TODO: add splitVar if it exists
    //     }
    //     const urlBase = "/Statistics.query";
    //     let url = new URL(urlBase, window.location.origin);

    //     fetch( url, { body: requestData })
    //       .then((response) => response.ok ? response.json() : Promise.reject(response))
    //       .then((statJson) => {
    //         setCurrentStatistic(currentStatistic => [...currentStatistic, JSON.stringify(statJson)]);
    //       })
    //       .catch(handleError);

    //   })
    //   .catch(handleError);

    // currently, variables are being sent in parameter
    const urlBase = "/Statistics.query";
    let url = new URL(urlBase, window.location.origin);
    url.searchParams.set("name", stat.name);
    url.searchParams.set("xVar", stat.xVar['jcr:uuid']);
    url.searchParams.set("yVar", stat.yVar['jcr:uuid']);

    fetch(url)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((statJson) => {
        setCurrentStatistic(currentStatistic => [...currentStatistic, JSON.stringify(statJson)]);
      })
      .catch(handleError);
  }

  let fetchAll = (data) => {
    data.map((stat) => fetchStat(stat))
  }

  return (
    <React.Fragment>
      <Grid container spacing={3}>
        {currentStatistic && currentStatistic.map((stat) => {
          return(
            <Grid item lg={12} xl={6} key={stat["@path"]}>
              <Card>
                <CardContent>
                    <Grid container alignItems='flex-end' spacing={2}>
                      <Grid item xs={12}><Typography variant="body2">{stat}</Typography></Grid>
                    </Grid>
                </CardContent>
              </Card>
            </Grid>
          )
        })}
      </Grid>
    </React.Fragment>
  );
}

export default withStyles(statisticsStyle)(UserStatistics);
