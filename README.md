# About the Project

This is a finished Java implementation for the codecrafters
["Build Your Own DNS server" Challenge](https://app.codecrafters.io/courses/dns-server/overview).
This code implements functionality for all stages (and extensions) of the
challenge as of 2025-02-13.

## What can it do?

1. Respond to multiple questions with a hardcoded `A` record of `8.8.8.8`.
2. Handle compressed label sequences in questions.
3. Forward queries for `A` records to an upstream resolver specified with
   `--resolver <address>` on the command line. That upstream resolver must:
    * Always respond with an answer section for the queries.
    * Generate a response that does not contain other sections like (authority
      section and additional section)
    * Only respond when there is only one question in the question section (this
      DNS server handles splitting questions into multiple packets then merges
      the answers into a single response packet).

## Running Locally

You will need Java 23 (or later) and maven installed to run this code. The program can then be run with:

`./your_program.sh --resolver <optional-upstream-ressolver>`

## Test Run Video

A short video of the code being run in the codecrafters test environment:

https://github.com/user-attachments/assets/c0b337d0-bf1f-465c-acc0-f678b9ccc099
