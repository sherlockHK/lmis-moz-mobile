#!/usr/bin/env ruby
require_relative 'local_test_steps.rb'

update_mis_moz

puts "Running ft"
ftResult = run_ft
exit 1 if !ftResult
puts "Finished ft"