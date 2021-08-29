(ns miyatake.core
    (:require
      [reagent.core :as r]
      [reagent.dom :as d]
      [recharts :as rs]
      [ajax.core :refer [GET]]
      [miyatake.data :as miyatake-data]))

(defonce raw-data
  (r/atom []))

(defonce temp-data
  (r/atom []))

(defonce dashboard-data
  (r/atom []))

(defonce selecting (r/atom "กระบี่"))
(defonce selected (r/atom []))

(def first-to-second-wave-api-endpoint
  "https://covid19.ddc.moph.go.th/api/Cases/round-1to2-by-provinces")
(def third-wave-api-endpoint
  "https://covid19.ddc.moph.go.th/api/Cases/timeline-cases-by-provinces")


(defn get-data!
 "Getting JSON data from source api and store to raw-data atom"
 []
 (GET
  third-wave-api-endpoint
  {:handler (fn [res]
              (reset! raw-data res))
   :response-format :json}))

(defn data-by-province [province]
 (filter #(= (% "province") province) @raw-data))

(def krabi (filter #(= (% "province") "กระบี่") @temp-data))

(defn group-by-month [data]
  (group-by #(apply str (take 7 (% "txn_date"))) data))

(defn new-case-by-month [data province]
  (for [[k v] data]
    (let [monthly-case (reduce + (map #(% "new_case") v))]
      {"month" k province monthly-case})))

(defn get-province [province]
  (let [data-province (data-by-province province)
        monthly-group (group-by-month data-province)
        new-cases (new-case-by-month monthly-group province)]
    (sort-by #(% "month") new-cases)))

(defn update-dashboard [province]
  (if (empty? @dashboard-data)
    (reset! dashboard-data (get-province province))
    (reset! dashboard-data (map merge @dashboard-data (get-province province)))))

(defn rand-color []
  (.toString (rand-int 16rFFFFFF) 16))

(get-data!)

;; -------------------------
;; Views

(defn simple-line []
   [:div
    [:> rs/LineChart
     {:width 600
      :height 400
      :data @dashboard-data}
     [:> rs/CartesianGrid {:strokeDasharray "3 3"}]
     [:> rs/XAxis {:dataKey "month"}]
     [:> rs/YAxis]
     [:> rs/Legend]
     [:> rs/Tooltip]
     (for [province @selected]
       ^{:key province}
       [:> rs/Line {:type "monotone"
                    :dataKey province
                    :stroke (str "#" (miyatake-data/color-list province))}])]])


(defn home-page []
  [:div
   [:h1 "Thailand COVID-19 new cases by province"]
   [:p "Data fetch from " [:a {:href "https://covid19.ddc.moph.go.th/"} "Department of Disease Control."]]
   [simple-line]
   [:div
    [:h2 "Select Data"]
    [:p "Select a province, then click 'Add' to view data."]
    ;[:p "Try adding multiple counties to chart and clicking the 'Weight counts by population' button for comparison."]
    [:div
     [:fieldset
      [:label {:for "state-selector"} "Province "]
      [:select.state-selector {:name "state-selector"
                               :on-change #(reset! selecting (str (-> % .-target .-value)))}
       (for [province miyatake-data/province-choices]
           ^{:key province} [:option {:value province} province])]
      [:input.add-country
       {:type "button" :value "Add"
        :on-click (fn []
                    (swap! selected conj @selecting)
                    (update-dashboard @selecting))

        :disabled (some #(= % @selecting) @selected)}]]]
    [:div]
    [:h4 "Selected provinces"]
    [:p
     {:on-double-click #(reset! selected [])}
     (str "Double click to clear the plot: " (clojure.string/join ", " @selected))]]])

;; -------------------------
;; Initialize app

(defn mount-root []
  (d/render [home-page] (.getElementById js/document "app")))

(defn init! []
  (mount-root))
