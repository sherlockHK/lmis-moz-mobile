require 'calabash-android/calabash_steps'
require 'pry'

Given(/^I have added new drugs/) do
  if EnvConfig::STRESS_TEST
    steps %Q{
            And I initialize "300" products
        }
  else
    steps %Q{
            When I search product by fnm "08S01ZY" and select this item with quantity "2008"
       }
  end
end

Then(/^I check new drug quantity/) do
  if EnvConfig::STRESS_TEST
    steps %Q{
            Then I should see total:"300" on stock list page
        }
  else
    steps %Q{
            When I search product by fnm "08S01ZY" and select this item with quantity "2008"
       }
  end
end

Then(/^I select new drug "(.*?)" with SOH "(.*?)" quantity/) do |drugproperty, soh|
  steps %Q{
          When I search drug by fnm "#{drugproperty}"
      }
  q = query("android.widget.CheckBox id:'checkbox' checked:'false'")
  if !q.empty?
    touch(q)
    steps %Q{
              When I select the item called "#{drugproperty}"
              Then I enter quantity "#{soh}" on inventory page
          }
  end
end
