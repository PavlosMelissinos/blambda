(ns blambda.internal
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [com.grzm.awyeah.client.api :as aws]))

(defn endpoint-override [url]
  (let [[protocol rest] (str/split url #"://" 2)
        [hostname rest] (str/split rest #":" 2)
        [port _]        (str/split rest #"/" 2)]
    {:protocol (keyword protocol)
     :hostname hostname
     :port     (parse-long port)}))

(defn deploy-layer [{:keys [aws-region target-dir
                            layer-name layer-filename runtimes architectures
                            endpoint-url]}]
  (let [client (aws/client (merge {:api :lambda
                                   :region aws-region}
                                  (when endpoint-url
                                    {:endpoint-override (endpoint-override endpoint-url)})))
        zipfile (fs/read-all-bytes layer-filename)
        request (merge {:LayerName layer-name
                        :Content {:ZipFile zipfile}}
                       (when runtimes {:CompatibleRuntimes runtimes})
                       (when architectures {:CompatibleArchitectures architectures}))
        _ (println "Publishing layer version for layer" layer-name)
        res (aws/invoke client {:op :PublishLayerVersion
                                :request request})]
    (if (:cognitect.anomalies/category res)
      (prn "Error:" res)
      (println "Published layer" (:LayerVersionArn res)))))
