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

import { useState } from "react";

import { Typography, withStyles } from "@material-ui/core";

import PropTypes from "prop-types";

import MultipleChoice from "./MultipleChoice";
import Question from "./Question";
import QuestionnaireStyle from "./QuestionnaireStyle";

// Component that renders a multiple choice question, with optional number input
// Selected answers are placed in a series of <input type="hidden"> tags for
// submission.
//
// props:
//  max: Integer denoting maximum number of options that may be selected
//  min: Integer denoting minimum number of options that may be selected
//  name: String containing the question to ask
//  defaults: Array of objects, each with an "id" representing internal ID
//            and a "value" denoting what will be displayed
//  userInput: Either "input", "textbox", or undefined denoting the type of
//             user input. Currently, only "input" is supported
//  maxValue: The maximum allowed input value
//  minValue: The minimum allowed input value
//  type: One of "integer" or "float"
//  errorText: String to display when the input is not valid
//
// sample usage:
// <NumberQuestion
//    name="Please enter the patient's age"
//    defaults={[
//      {"id": "<18", "label": "<18"}
//    ]}
//    max={1}
//    minValue={18}
//    type="integer"
//    errorText="Please enter an age above 18, or select the <18 option"
//    />
function NumberQuestion(props) {
  let {defaults, max, min, name, userInput, minValue, maxValue, type, errorText, ...rest} = props;
  const [error, setError] = useState(false);

  // Callback function for our min/max
  let checkNumber = (text) => {
    let value = 0;
    if (type === "integer") {
      // Test that it is an integer
      if (!/^[-+]?\d*$/.test(text)) {
        setError(true);
        return;
      }

      value = parseInt(text);
    } else if (type === "float") {
      value = Number(text);

      // Reject whitespace and non-numbers
      if (/^\s*$/.test(text) || isNaN(value)) {
        setError(true);
        return;
      }
    }

    // Test that it is within our min/max (if they are defined)
    if ((typeof minValue !== 'undefined' && value < minValue) ||
      (typeof maxValue !== 'undefined' && value > maxValue)) {
      setError(true);
      return;
    }
    setError(false);
  }

  return (
    <Question
      text={name}
      >
      {error && <Typography color='error'>{errorText}</Typography>}
      <MultipleChoice
        max={max}
        min={min}
        defaults={defaults}
        input={userInput==="input"}
        textbox={userInput==="textbox"}
        onChange={checkNumber}
        additionalInputProps={{
          type: "number",
          min: minValue,
          max: maxValue
        }}
        {...rest}
        />
    </Question>);
}

NumberQuestion.propTypes = {
  classes: PropTypes.object.isRequired,
  name: PropTypes.string,
  min: PropTypes.number,
  max: PropTypes.number,
  defaults: PropTypes.array,
  userInput: PropTypes.oneOf([undefined, "input", "textbox"]),
  type: PropTypes.oneOf(['integer', 'float']),
  minValue: PropTypes.number,
  maxValue: PropTypes.number,
  errorText: PropTypes.string
};

NumberQuestion.defaultProps = {
  errorText: "Invalid input",
  type: 'float'
};

export default withStyles(QuestionnaireStyle)(NumberQuestion);
