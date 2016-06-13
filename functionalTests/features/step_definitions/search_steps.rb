require 'calabash-android/calabash_steps'
require 'pry'
require 'date'
require File.dirname(__FILE__) + '/env_config'

username = EnvConfig.getConfig()[:username]
password = EnvConfig.getConfig()[:password]

index = 0
pre_name = ""

Then /^I navigate back/ do
    tap_when_element_exists("* contentDescription:'Navigate up'")
end

Then /^I scroll down until I see the "([^\"]*)"/ do |text|
  unless has_text?(text)
    scroll_down
  end
end

When(/^I search product by fnm "(.*?)" and select this item with quantity "(.*?)"/) do |fnm,quantity|
    steps %Q{
        When I search drug by fnm "#{fnm}"
    }
    q = query("android.widget.CheckBox id:'checkbox' checked:'false'")
    if !q.empty?
        touch(q)
        steps %Q{
            When I select the item called "#{fnm}"
            Then I enter quantity "#{quantity}" on inventory page
        }
    end
    steps %Q{
        And I clean search bar
    }
end

When(/^I search product by primary name "(.*?)" and select this item with quantity "(.*?)"/) do |primary,quantity|
    steps %Q{
        When I search "#{primary}"
    }
    q = query("android.widget.CheckBox id:'checkbox' checked:'false'")
    if !q.empty?
        touch(q)
        steps %Q{
            When I select the item called "#{primary}"
            Then I enter quantity "#{quantity}" on inventory page
        }
    end
    steps %Q{
        And I clean search bar
    }
end

Then(/^I shouldn't see product "(.*?)" in this page$/) do |productProperty|
    steps %Q{
       When I search drug by fnm "#{productProperty}"
    }
    list = query("android.widget.TextView id:'product_name'")
    if list.empty?
        steps %Q{
          And I clean search bar
      }
    else
       fail "#{productProperty}" "should not see in this page"
   end

end

Then(/^I should see product "(.*?)" in this page$/) do |productProperty|
    steps %Q{
       When I search drug by fnm "#{productProperty}"
    }
    list = query("android.widget.RecyclerView id:'products_list'")
    if !list.empty?
        steps %Q{
            And I clean search bar
        }
    end
end

And(/^I clean search bar/) do
    search_bar = query("android.support.v7.widget.SearchView id:'action_search'")
    clear_text_in(search_bar)
end

When(/^I search drug by fnm "(.*?)"$/) do |fnm|
    search_bar = query("android.support.v7.widget.SearchView id:'action_search'").first
    touch(search_bar)
    enter_text("android.support.v7.widget.SearchView id:'action_search'", fnm)
end

When(/^I search "(.*?)"$/) do |keyword|
    search_bar = query("android.support.v7.widget.SearchView id:'action_search'")
    touch(search_bar)
    enter_text("android.support.v7.widget.SearchView id:'action_search'", keyword)
end
